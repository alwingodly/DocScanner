package com.example.docscanner.data.camera

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * ML Kit Document Scanner — replaces DocumentDetector.
 *
 * What changed vs OpenCV DocumentDetector:
 * ┌─────────────────────────┬──────────────────────────────────────────┐
 * │ OpenCV DocumentDetector │ ML Kit DocumentScanner                   │
 * ├─────────────────────────┼──────────────────────────────────────────┤
 * │ Manual CLAHE + Canny    │ Built-in — no manual preprocessing       │
 * │ Hough line fallback     │ Built-in — handles low contrast scenes   │
 * │ Manual corner ordering  │ Built-in — returns clean page URIs       │
 * │ ~200 lines of math      │ ~50 lines of integration code            │
 * │ Requires OpenCV .so     │ Ships via Google Play Services — 0 APK   │
 * └─────────────────────────┴──────────────────────────────────────────┘
 *
 * Usage in your Activity/Fragment:
 *
 *   // 1. Register the launcher (do this in onCreate / composable setup)
 *   val launcher = registerForActivityResult(
 *       ActivityResultContracts.StartIntentSenderForResult()
 *   ) { result ->
 *       DocumentScanner.handleResult(result.resultCode, result.data) { pages, pdfUri ->
 *           // pages = list of JPEG URIs (one per scanned page)
 *           // pdfUri = combined PDF URI (null if only JPEG was requested)
 *       }
 *   }
 *
 *   // 2. Launch the scanner
 *   DocumentScanner.launch(activity, launcher)
 */
object DocumentScanner {

    /**
     * Launch the ML Kit document scanner UI.
     *
     * This opens Google's built-in scanning UI which handles:
     *  - Real-time document edge detection (replaces your OpenCV CLAHE + Canny pipeline)
     *  - Perspective correction (replaces perspectiveTransform in DocumentProcessor)
     *  - Image enhancement (replaces your filter pipeline for the scan step)
     *  - Multi-page scanning
     *  - Gallery import
     *
     * @param activity  The calling Activity (needed to start the scanner intent)
     * @param launcher  ActivityResultLauncher registered with StartIntentSenderForResult
     * @param pageLimit Maximum pages to scan (default 10). Pass 1 for single-page mode.
     * @param onError   Called if the scanner fails to initialize (rare — usually a Play Services issue)
     */
    fun launch(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        pageLimit: Int = 10,
        onError: (Exception) -> Unit = {}
    ) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)          // allow picking from gallery too
            .setPageLimit(pageLimit)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)      // FULL = edge detection + auto-enhance
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Parse the result returned by the scanner Activity.
     *
     * Call this inside your ActivityResultLauncher callback.
     *
     * @param resultCode  The result code from the scanner Activity
     * @param data        The intent data from the result
     * @param onSuccess   Called with (pageUris, pdfUri) on successful scan
     * @param onCancelled Called if the user cancelled the scan
     * @param onError     Called on error
     */
    fun handleResult(
        resultCode: Int,
        data: android.content.Intent?,
        onSuccess: (pages: List<Uri>, pdfUri: Uri?) -> Unit,
        onCancelled: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
                if (scanResult == null) {
                    onError(Exception("Scanner returned null result"))
                    return
                }
                // Page URIs — each is a JPEG of one scanned page (perspective-corrected + enhanced)
                val pageUris = scanResult.pages?.map { it.imageUri } ?: emptyList()

                // PDF URI — all pages combined (null if PDF format wasn't requested or no pages)
                val pdfUri = scanResult.pdf?.uri

                onSuccess(pageUris, pdfUri)
            }
            Activity.RESULT_CANCELED -> onCancelled()
            else -> onError(Exception("Unexpected result code: $resultCode"))
        }
    }
}