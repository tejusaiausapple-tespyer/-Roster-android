package com.surainvestments.roster.ui.staff.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * In-app, camera-only capture (no gallery affordance anywhere) via CameraX — replaces the previous
 * system-Camera-app-intent flow with a live preview + shutter, matching the plan's original spec.
 * Callers own the permission check; this assumes `CAMERA` is already granted by the time it's shown.
 *
 * `ImageCapture.OnImageCapturedCallback` hands back a JPEG-backed [ImageProxy] with no built-in
 * bitmap conversion in this CameraX version (verified against the resolved 1.6.1 jar — there's no
 * public `ImageProxy.toBitmap()`), so the JPEG bytes are decoded manually and the result rotated by
 * `imageInfo.rotationDegrees` — skipping that rotation is the single most common CameraX capture
 * bug (a sideways/upside-down photo).
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var isReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewView) {
        val cameraProvider = try {
            awaitCameraProvider(context)
        } catch (e: Exception) {
            error = "Couldn't start the camera."
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            isReady = true
        } catch (e: Exception) {
            error = "Couldn't start the camera."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (!isReady && error == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        error?.let {
            Banner(
                kind = BannerKind.Error,
                title = it,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
        }

        ShutterButton(
            enabled = isReady && !isCapturing,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            onClick = {
                isCapturing = true
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toUprightBitmap()
                            image.close()
                            isCapturing = false
                            if (bitmap != null) onCaptured(bitmap) else error = "Couldn't process the photo."
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                            error = "Couldn't capture photo."
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

/** Bridges CameraX's `ListenableFuture` without pulling in a Guava-coroutines dependency for one call. */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(context),
        )
    }

/** Decodes the JPEG-backed [ImageProxy] and rotates it upright per `imageInfo.rotationDegrees`. */
private fun ImageProxy.toUprightBitmap(): Bitmap? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
