// data/ocr/ner/WordPieceTokenizer.kt
package com.example.docscanner.data.ocr.ner

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Minimal WordPiece tokenizer compatible with BERT `vocab.txt` files.
 *
 * Supports:
 *   • BasicTokenizer whitespace + punctuation splitting (ASCII-centric; sufficient
 *     for romanised Indian names on OCR output).
 *   • WordPiece greedy longest-match-first subword segmentation.
 *   • Char-offset tracking so downstream code can map subword indices back to
 *     character ranges in the original string.
 *
 * For Devanagari / Tamil / other Indic scripts on IDs, most OCR text is
 * already latinised. If you ship a multilingual vocab, add Unicode category
 * handling in [splitOnPunctuation] and [isWhitespace].
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkToken: String = "[UNK]",
    private val clsToken: String = "[CLS]",
    private val sepToken: String = "[SEP]",
    private val padToken: String = "[PAD]",
    private val maxInputCharsPerWord: Int = 100,
    private val doLowerCase: Boolean = true,
) {
    data class Token(
        val text: String,
        val id: Int,
        val originalStart: Int,
        val originalEnd: Int,
        val isSubword: Boolean,
    )

    val clsId: Int = vocab[clsToken] ?: error("vocab missing $clsToken")
    val sepId: Int = vocab[sepToken] ?: error("vocab missing $sepToken")
    val padId: Int = vocab[padToken] ?: 0
    val unkId: Int = vocab[unkToken] ?: error("vocab missing $unkToken")

    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val source = if (doLowerCase) text.lowercase() else text

        var i = 0
        while (i < source.length) {
            // Skip whitespace
            while (i < source.length && source[i].isWhitespace()) i++
            if (i >= source.length) break

            // Accumulate a "word"
            val wordStart = i
            while (i < source.length && !source[i].isWhitespace() && !isPunct(source[i])) i++
            val wordEnd = i

            if (wordEnd > wordStart) {
                wordPiecesFor(source, wordStart, wordEnd).forEach { tokens += it }
            }

            // Emit a standalone punctuation token (if any)
            if (i < source.length && isPunct(source[i])) {
                val ch = source[i].toString()
                val id = vocab[ch] ?: unkId
                tokens += Token(
                    text = if (id == unkId) unkToken else ch,
                    id = id,
                    originalStart = i,
                    originalEnd = i + 1,
                    isSubword = false,
                )
                i++
            }
        }
        return tokens
    }

    private fun wordPiecesFor(source: String, start: Int, end: Int): List<Token> {
        val word = source.substring(start, end)
        if (word.length > maxInputCharsPerWord) {
            return listOf(Token(unkToken, unkId, start, end, false))
        }

        val pieces = mutableListOf<Token>()
        var pos = 0
        while (pos < word.length) {
            var matchLen = 0
            var matchToken: String? = null
            // Greedy longest-match-first
            for (len in word.length - pos downTo 1) {
                var candidate = word.substring(pos, pos + len)
                if (pos > 0) candidate = "##$candidate"
                if (vocab.containsKey(candidate)) {
                    matchToken = candidate
                    matchLen = len
                    break
                }
            }
            if (matchToken == null) {
                return listOf(Token(unkToken, unkId, start, end, false))
            }
            pieces += Token(
                text = matchToken,
                id = vocab[matchToken]!!,
                originalStart = start + pos,
                originalEnd = start + pos + matchLen,
                isSubword = pos > 0,
            )
            pos += matchLen
        }
        return pieces
    }

    private fun isPunct(c: Char): Boolean {
        val code = c.code
        return (code in 33..47) || (code in 58..64) ||
                (code in 91..96) || (code in 123..126) ||
                c.category == CharCategory.OTHER_PUNCTUATION ||
                c.category == CharCategory.DASH_PUNCTUATION ||
                c.category == CharCategory.START_PUNCTUATION ||
                c.category == CharCategory.END_PUNCTUATION
    }

    companion object {
        /** Loads a BERT-style vocab.txt file from assets. */
        fun loadVocab(context: Context, assetPath: String): Map<String, Int> {
            val map = LinkedHashMap<String, Int>(32_000)
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEachIndexed { idx, line -> map[line.trim()] = idx }
                }
            }
            return map
        }
    }
}