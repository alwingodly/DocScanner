package com.example.docscanner.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
 * PDF generation uses Android's built-in PdfDocument API — no external library.
 * File saving uses MediaStore API (Android 10+) for proper scoped storage.
 *
 * Why @Singleton?
 * - Needs Application context (injected by Hilt)
 * - Stateless, so one instance is fine
 *
 * Why Dispatchers.IO?
 * - File writing is IO-bound (disk access)
 * - Different from DocumentProcessor which uses Dispatchers.Default (CPU-bound)
 */
@Singleton
class DocumentExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Export all pages as a single PDF file.
     *
     * How PDF generation works:
     * 1. Create a PdfDocument
     * 2. For each page, create an A4-sized PDF page
     * 3. Scale the bitmap to fit within A4 (maintaining aspect ratio)
     * 4. Draw the bitmap centered on the PDF page
     * 5. Save to device storage via MediaStore
     *
     * A4 at 72 DPI = 595 x 842 points (standard PDF units)
     *
     * @param pages List of scanned pages
     * @param fileName Name for the output file (without extension)
     * @return URI of the saved file, or null on failure
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

                // A4 dimensions in PDF points (72 DPI)
                val a4Width = 595
                val a4Height = 842

                // Scale bitmap to fit A4, maintaining aspect ratio
                val scale = minOf(
                    a4Width.toFloat() / bitmap.width,
                    a4Height.toFloat() / bitmap.height
                )
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()

                // Create PDF page
                val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)

                // Center bitmap on page
                val canvas: Canvas = pdfPage.canvas
                val left = (a4Width - scaledWidth) / 2f
                val top = (a4Height - scaledHeight) / 2f

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                canvas.drawBitmap(scaledBitmap, left, top, null)

                // Recycle scaled bitmap if it's a new object
                if (scaledBitmap !== bitmap) {
                    scaledBitmap.recycle()
                }

                pdfDocument.finishPage(pdfPage)
            }

            // Save to storage
            val uri = saveFile(fileName, "pdf") { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            pdfDocument.close()
            uri
        } catch (e: Exception) {
            pdfDocument.close()
            e.printStackTrace()
            null
        }
    }

    /**
     * Export a single page as JPEG or PNG.
     *
     * @param page The page to export
     * @param fileName Name for the output file
     * @param format JPEG or PNG
     * @return URI of the saved file
     */
    suspend fun exportAsImage(
        page: ScannedPage,
        fileName: String,
        format: ExportFormat
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = page.displayBitmap

        val compressFormat = when (format) {
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == ExportFormat.PNG) 100 else 95

        saveFile(fileName, format.extension) { outputStream ->
            bitmap.compress(compressFormat, quality, outputStream)
        }
    }

    /**
     * Export all pages as individual images.
     *
     * @return List of URIs for all saved images
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
     * Save a file using MediaStore (Android 10+) or legacy file API.
     *
     * MediaStore is required for Android 10+ (scoped storage).
     * The file appears in the user's Documents or Pictures folder.
     *
     * @param fileName File name without extension
     * @param extension File extension (pdf, jpg, png)
     * @param writeContent Lambda that writes the file content to the stream
     * @return URI of the saved file
     */
    private fun saveFile(
        fileName: String,
        extension: String,
        writeContent: (java.io.OutputStream) -> Unit
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ── Modern: MediaStore API ──
            val mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> "application/octet-stream"
            }

            val subDir = if (extension == "pdf") {
                Environment.DIRECTORY_DOCUMENTS
            } else {
                Environment.DIRECTORY_PICTURES
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$subDir/DocScanner")
            }

            val collection = if (extension == "pdf") {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val uri = context.contentResolver.insert(collection, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    writeContent(outputStream)
                }
            }
            uri
        } else {
            // ── Legacy: direct file access ──
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "DocScanner"
            )
            dir.mkdirs()
            val file = File(dir, "$fileName.$extension")
            FileOutputStream(file).use { writeContent(it) }
            Uri.fromFile(file)
        }
    }
}