package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated Live In-App Camera QR Code Scanner Sheet.
 * Continuously analyzes camera video stream frames using ZXing, instantly detecting
 * and decoding QR codes without delegating to a third-party camera app or taking a photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveQrCodeScannerSheet(
    onQrCodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar with Title and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "Scan Receiver QR Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (hasCameraPermission) {
                // Live Viewfinder Camera Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                    val hasHandledResult = remember { AtomicBoolean(false) }

                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                val multiFormatReader = MultiFormatReader()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (hasHandledResult.get()) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val decodedText = decodeBarcode(imageProxy, multiFormatReader)
                                    if (decodedText != null && !hasHandledResult.getAndSet(true)) {
                                        previewView.post {
                                            onQrCodeDetected(decodedText)
                                        }
                                    }
                                    imageProxy.close()
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target Reticle / Scanner Overlay
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(24.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Point camera directly at the QR code on the receiver's phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                // Permission request notice
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera permission is required to scan QR codes live",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Allow Camera Access")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Extracts grayscale plane from ImageProxy and decodes QR matrix using ZXing.
 * Accounts for camera sensor rotation so that portrait orientation scans accurately.
 */
private fun decodeBarcode(
    imageProxy: ImageProxy,
    reader: MultiFormatReader
): String? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height

        val rotatedBytes = when (rotationDegrees) {
            90 -> rotateNv21Degree90(bytes, width, height)
            180 -> rotateNv21Degree180(bytes, width, height)
            270 -> rotateNv21Degree270(bytes, width, height)
            else -> bytes
        }

        val effectiveWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val effectiveHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

        val source = PlanarYUVLuminanceSource(
            rotatedBytes,
            effectiveWidth,
            effectiveHeight,
            0,
            0,
            effectiveWidth,
            effectiveHeight,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(binaryBitmap)
        result.text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

private fun rotateNv21Degree90(data: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(data.size)
    var i = 0
    for (x in 0 until width) {
        for (y in height - 1 downTo 0) {
            rotated[i++] = data[y * width + x]
        }
    }
    return rotated
}

private fun rotateNv21Degree180(data: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(data.size)
    var i = 0
    for (y in height - 1 downTo 0) {
        for (x in width - 1 downTo 0) {
            rotated[i++] = data[y * width + x]
        }
    }
    return rotated
}

private fun rotateNv21Degree270(data: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(data.size)
    var i = 0
    for (x in width - 1 downTo 0) {
        for (y in 0 until height) {
            rotated[i++] = data[y * width + x]
        }
    }
    return rotated
}
