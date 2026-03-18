package com.example.docscanner.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.docscanner.domain.model.ExportFormat
import com.example.docscanner.domain.model.ScannedPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles exporting scanned pages to PDF or image files.
 *
 * Files are saved to app-private storage (context.filesDir/documents/).
 * NOT visible in Gallery or Files app — only accessible within the app.
 *
 * Uses FileProvider URIs so files can still be shared via intents if needed.
 */
@Singleton
class DocumentExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** App-private directory for all exported documents */
    private val docsDir: File
        get() = File(context.filesDir, "documents").also { it.mkdirs() }

    /**
     * Export all pages as a single PDF file.
     *
     * A4 at 72 DPI = 595 x 842 points (standard PDF units).
     * Each page's bitmap is scaled to fit A4 maintaining aspect ratio,
     * then drawn centered on the PDF page.
     */
    suspend fun exportToPdf(
        pages: List<ScannedPage>,
        fileName: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()

        try {
            pages.forEachIndexed { index, page ->
                val bitmap = page.displayBitmap

                val a4Width = 595
                val a4Height = 842

                val scale = minOf(
                    a4Width.toFloat() / bitmap.width,
                    a4Height.toFloat() / bitmap.height
                )
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()

                val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)

                val canvas: Canvas = pdfPage.canvas
                val left = (a4Width - scaledWidth) / 2f
                val top = (a4Height - scaledHeight) / 2f

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                canvas.drawBitmap(scaledBitmap, left, top, null)

                if (scaledBitmap !== bitmap) {
                    scaledBitmap.recycle()
                }

                pdfDocument.finishPage(pdfPage)
            }

            val file = File(docsDir, "$fileName.pdf")
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()

            // Return a file URI that works with our viewer
            Uri.fromFile(file)
        } catch (e: Exception) {
            pdfDocument.close()
            e.printStackTrace()
            null
        }
    }

    /**
     * Export a single page as JPEG or PNG.
     */
    suspend fun exportAsImage(
        page: ScannedPage,
        fileName: String,
        format: ExportFormat
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = page.displayBitmap

            val compressFormat = when (format) {
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            val quality = if (format == ExportFormat.PNG) 100 else 95

            val file = File(docsDir, "$fileName.${format.extension}")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(compressFormat, quality, outputStream)
            }

            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Export all pages as individual images.
     */
    suspend fun exportAllAsImages(
        pages: List<ScannedPage>,
        baseName: String,
        format: ExportFormat
    ): List<Uri> = withContext(Dispatchers.IO) {
        pages.mapIndexedNotNull { index, page ->
            exportAsImage(page, "${baseName}_${index + 1}", format)
        }
    }

    /**
     * Get a FileProvider URI for sharing a file externally (via share intent).
     * Only use this when the user explicitly wants to share/export.
     */
    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}