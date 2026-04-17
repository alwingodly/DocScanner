// data/ocr/ner/TfLiteNerExtractor.kt
package com.example.docscanner.data.ocr.ner

import android.content.Context
import android.util.Log
import com.example.docscanner.domain.model.NerEntity
import com.example.docscanner.domain.model.NerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite BERT token-classification NER.
 *
 * Expected model I/O:
 *   Input 0:  int32[1, seqLen]  — input_ids
 *   Input 1:  int32[1, seqLen]  — attention_mask
 *   Input 2:  int32[1, seqLen]  — token_type_ids  (optional, sent as zeros)
 *   Output 0: float32[1, seqLen, numLabels]  — logits
 *
 * Assets required (put in app/src/main/assets/ner/):
 *   ner/model.tflite      — quantized INT8 transformer model
 *   ner/vocab.txt         — WordPiece vocab
 *   ner/labels.txt        — BIO label set, one per line, order = model output dim
 *
 * Label format (BIO):
 *   O
 *   B-PER  I-PER
 *   B-LOC  I-LOC
 *   B-ORG  I-ORG
 *   B-DATE I-DATE
 *   B-MISC I-MISC
 */
@Singleton
class TfLiteNerExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NerExtractor {

    private val tag = "TfLiteNer"
    private val seqLen = 128
    private val modelAsset = "ner/model.tflite"
    private val vocabAsset = "ner/vocab.txt"
    private val labelsAsset = "ner/labels.txt"

    private val initMutex = Mutex()
    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var tokenizer: WordPieceTokenizer? = null
    @Volatile private var labels: List<String> = emptyList()
    @Volatile private var initFailed = false

    override suspend fun extract(text: String): List<NerEntity> = withContext(Dispatchers.Default) {
        if (!ensureInitialized()) return@withContext emptyList()
        if (text.isBlank()) return@withContext emptyList()

        val tok = tokenizer!!
        val interp = interpreter!!
        val labelList = labels

        val pieces = tok.tokenize(text)
        if (pieces.isEmpty()) return@withContext emptyList()

        // Split into seqLen-sized chunks (account for [CLS] and [SEP])
        val maxContent = seqLen - 2
        val allEntities = mutableListOf<NerEntity>()

        pieces.chunked(maxContent).forEach { chunk ->
            val ids = IntArray(seqLen) { tok.padId }
            val mask = IntArray(seqLen)
            val types = IntArray(seqLen)

            ids[0] = tok.clsId
            mask[0] = 1
            for (i in chunk.indices) {
                ids[i + 1] = chunk[i].id
                mask[i + 1] = 1
            }
            ids[chunk.size + 1] = tok.sepId
            mask[chunk.size + 1] = 1

            val inputIds = intArrayToByteBuffer(ids)
            val attnMask = intArrayToByteBuffer(mask)
            val tokenTypes = intArrayToByteBuffer(types)
            val output = Array(1) { Array(seqLen) { FloatArray(labelList.size) } }

            try {
                interp.runForMultipleInputsOutputs(
                    arrayOf<Any>(inputIds, attnMask, tokenTypes),
                    mapOf(0 to output),
                )
            } catch (t: Throwable) {
                Log.e(tag, "inference failed", t)
                return@forEach
            }

            // Decode BIO labels, keeping char offsets from the original text
            val chunkEntities = decodeBio(
                logits = output[0],
                tokens = chunk,
                labels = labelList,
                originalText = text,
            )
            allEntities += chunkEntities
        }

        mergeAdjacent(allEntities)
    }

    private suspend fun ensureInitialized(): Boolean {
        if (initFailed) return false
        if (interpreter != null && tokenizer != null && labels.isNotEmpty()) return true
        return initMutex.withLock {
            if (initFailed) return@withLock false
            if (interpreter != null && tokenizer != null && labels.isNotEmpty()) return@withLock true
            try {
                val model = loadModelFile(modelAsset)
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                interpreter = Interpreter(model, options)

                val vocab = WordPieceTokenizer.loadVocab(context, vocabAsset)
                tokenizer = WordPieceTokenizer(vocab)

                labels = context.assets.open(labelsAsset).bufferedReader().useLines { seq ->
                    seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
                Log.d(tag, "initialized: ${labels.size} labels, vocab=${vocab.size}")
                true
            } catch (t: Throwable) {
                Log.w(tag, "NER init failed, falling back to regex", t)
                initFailed = true
                interpreter?.close()
                interpreter = null
                tokenizer = null
                false
            }
        }
    }

    private fun loadModelFile(asset: String): MappedByteBuffer {
        val afd = context.assets.openFd(asset)
        afd.createInputStream().channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun intArrayToByteBuffer(arr: IntArray): ByteBuffer {
        val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
        arr.forEach { bb.putInt(it) }
        bb.rewind()
        return bb
    }

    /**
     * Consumes token logits + BIO labels → NerEntity list with correct char offsets.
     * Handles subword pieces by attaching them to the span started at their head token.
     */
    private fun decodeBio(
        logits: Array<FloatArray>,
        tokens: List<WordPieceTokenizer.Token>,
        labels: List<String>,
        originalText: String,
    ): List<NerEntity> {
        val entities = mutableListOf<NerEntity>()

        // Token index in logits: 0 = [CLS], 1..N = content, N+1 = [SEP]
        var currentType: NerType? = null
        var currentStart = -1
        var currentEnd = -1
        var currentScoreSum = 0f
        var currentTokenCount = 0

        fun flush() {
            if (currentType != null && currentStart >= 0 && currentEnd > currentStart) {
                val span = originalText.substring(
                    currentStart.coerceIn(0, originalText.length),
                    currentEnd.coerceIn(0, originalText.length),
                )
                if (span.isNotBlank()) {
                    entities += NerEntity(
                        type = currentType!!,
                        text = span.trim(),
                        startChar = currentStart,
                        endChar = currentEnd,
                        confidence = (currentScoreSum / currentTokenCount).coerceIn(0f, 1f),
                    )
                }
            }
            currentType = null
            currentStart = -1
            currentEnd = -1
            currentScoreSum = 0f
            currentTokenCount = 0
        }

        for (i in tokens.indices) {
            val outputIdx = i + 1 // skip [CLS]
            if (outputIdx >= logits.size) break
            val row = logits[outputIdx]
            val (bestIdx, bestScore) = argmaxSoftmax(row)
            val label = labels.getOrNull(bestIdx) ?: "O"

            val token = tokens[i]

            when {
                label == "O" -> flush()
                label.startsWith("B-") -> {
                    flush()
                    currentType = bioToNerType(label.substring(2))
                    currentStart = token.originalStart
                    currentEnd = token.originalEnd
                    currentScoreSum = bestScore
                    currentTokenCount = 1
                }
                label.startsWith("I-") -> {
                    val iType = bioToNerType(label.substring(2))
                    if (currentType == iType) {
                        currentEnd = token.originalEnd
                        currentScoreSum += bestScore
                        currentTokenCount++
                    } else {
                        // Disagreement: treat as new span (strict BIO relaxed for OCR noise)
                        flush()
                        currentType = iType
                        currentStart = token.originalStart
                        currentEnd = token.originalEnd
                        currentScoreSum = bestScore
                        currentTokenCount = 1
                    }
                }
                else -> flush()
            }
        }
        flush()
        return entities
    }

    private fun argmaxSoftmax(logits: FloatArray): Pair<Int, Float> {
        var maxIdx = 0
        var max = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > max) { max = logits[i]; maxIdx = i }
        }
        // Softmax of the max vs. all
        var sum = 0f
        for (v in logits) sum += kotlin.math.exp(v - max)
        val prob = 1f / sum
        return maxIdx to prob
    }

    private fun bioToNerType(tag: String): NerType = when (tag.uppercase()) {
        "PER", "PERSON" -> NerType.PERSON
        "LOC", "LOCATION", "GPE" -> NerType.LOCATION
        "ORG", "ORGANIZATION" -> NerType.ORGANIZATION
        "DATE", "TIME" -> NerType.DATE
        else -> NerType.MISC
    }

    /** Merges entities that touch or overlap and share a type. */
    private fun mergeAdjacent(entities: List<NerEntity>): List<NerEntity> {
        if (entities.isEmpty()) return emptyList()
        val sorted = entities.sortedBy { it.startChar }
        val out = mutableListOf<NerEntity>()
        var cur = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.type == cur.type && next.startChar <= cur.endChar + 1) {
                cur = cur.copy(
                    endChar = maxOf(cur.endChar, next.endChar),
                    confidence = (cur.confidence + next.confidence) / 2f,
                )
            } else {
                out += cur; cur = next
            }
        }
        out += cur
        return out
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}