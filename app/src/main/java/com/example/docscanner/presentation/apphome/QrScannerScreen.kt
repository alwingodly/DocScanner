//package com.example.docscanner.presentation.apphome
//
//import android.util.Size
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Close
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.compose.ui.window.Dialog
//import androidx.compose.ui.window.DialogProperties
//import androidx.core.content.ContextCompat
//import com.google.mlkit.vision.barcode.BarcodeScannerOptions
//import com.google.mlkit.vision.barcode.BarcodeScanning
//import com.google.mlkit.vision.barcode.common.Barcode
//import com.google.mlkit.vision.common.InputImage
//import java.util.concurrent.Executors
//
//private val Coral = Color(0xFFE8603C)
//private val Ink = Color(0xFF1A1A2E)
//private val InkMid = Color(0xFF6B6878)
//
//@Composable
//fun QrScannerDialog(
//    onQrScanned: (String) -> Unit,
//    onDismiss: () -> Unit
//) {
//    var scanned by remember { mutableStateOf(false) }
//
//    Dialog(
//        onDismissRequest = onDismiss,
//        properties = DialogProperties(usePlatformDefaultWidth = false)
//    ) {
//        Box(
//            Modifier
//                .fillMaxSize()
//                .background(Color.Black)
//        ) {
//            // Camera preview
//            CameraPreviewWithQr(
//                onQrDetected = { value ->
//                    if (!scanned) {
//                        scanned = true
//                        onQrScanned(value)
//                    }
//                }
//            )
//
//            // Overlay
//            Column(
//                Modifier.fillMaxSize(),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                // Top bar
//                Row(
//                    Modifier
//                        .fillMaxWidth()
//                        .statusBarsPadding()
//                        .padding(8.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    IconButton(onClick = onDismiss) {
//                        Icon(
//                            Icons.Default.Close, "Close",
//                            tint = Color.White,
//                            modifier = Modifier.size(24.dp)
//                        )
//                    }
//                    Spacer(Modifier.width(8.dp))
//                    Text(
//                        "Scan QR Code",
//                        color = Color.White,
//                        fontSize = 17.sp,
//                        fontWeight = FontWeight.SemiBold
//                    )
//                }
//
//                Spacer(Modifier.weight(1f))
//
//                // Scan frame
//                Box(
//                    Modifier
//                        .size(240.dp)
//                        .border(2.dp, Coral, RoundedCornerShape(16.dp))
//                )
//
//                Spacer(Modifier.height(24.dp))
//
//                Text(
//                    "Point at the application QR code",
//                    color = Color.White.copy(0.8f),
//                    fontSize = 13.sp
//                )
//
//                Spacer(Modifier.weight(1f))
//            }
//        }
//    }
//}
//
//@Composable
//private fun CameraPreviewWithQr(onQrDetected: (String) -> Unit) {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val executor = remember { Executors.newSingleThreadExecutor() }
//
//    val options = remember {
//        BarcodeScannerOptions.Builder()
//            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
//            .build()
//    }
//    val scanner = remember { BarcodeScanning.getClient(options) }
//
//    AndroidView(
//        factory = { ctx ->
//            val previewView = PreviewView(ctx)
//            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
//            cameraProviderFuture.addListener({
//                val cameraProvider = cameraProviderFuture.get()
//                val preview = Preview.Builder().build().also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//                val analysis = ImageAnalysis.Builder()
//                    .setTargetResolution(Size(1280, 720))
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                    .build()
//                    .also { ia ->
//                        ia.setAnalyzer(executor) { imageProxy ->
//                            val mediaImage = imageProxy.image
//                            if (mediaImage != null) {
//                                val image = InputImage.fromMediaImage(
//                                    mediaImage,
//                                    imageProxy.imageInfo.rotationDegrees
//                                )
//                                scanner.process(image)
//                                    .addOnSuccessListener { barcodes ->
//                                        barcodes.firstOrNull()?.rawValue?.let {
//                                            onQrDetected(it)
//                                        }
//                                    }
//                                    .addOnCompleteListener { imageProxy.close() }
//                            } else {
//                                imageProxy.close()
//                            }
//                        }
//                    }
//                runCatching {
//                    cameraProvider.unbindAll()
//                    cameraProvider.bindToLifecycle(
//                        lifecycleOwner,
//                        CameraSelector.DEFAULT_BACK_CAMERA,
//                        preview,
//                        analysis
//                    )
//                }
//            }, ContextCompat.getMainExecutor(ctx))
//            previewView
//        },
//        modifier = Modifier.fillMaxSize()
//    )
//}