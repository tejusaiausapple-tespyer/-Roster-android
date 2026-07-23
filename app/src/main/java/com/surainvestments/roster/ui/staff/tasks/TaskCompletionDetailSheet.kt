package com.surainvestments.roster.ui.staff.tasks

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.surainvestments.roster.domain.model.RosterFormat
import com.surainvestments.roster.domain.model.RosterTask
import com.surainvestments.roster.domain.model.TaskCompletion
import com.surainvestments.roster.ui.components.Banner
import com.surainvestments.roster.ui.components.BannerKind
import com.surainvestments.roster.ui.components.PrimaryButton
import com.surainvestments.roster.ui.components.RosterCard
import com.surainvestments.roster.ui.components.ScreenPillTopBar
import com.surainvestments.roster.ui.components.ScreenPillTopBarHeight
import com.surainvestments.roster.ui.components.SecondaryButton
import com.surainvestments.roster.ui.components.SoftTag
import com.surainvestments.roster.ui.components.TopEdgeFade
import com.surainvestments.roster.ui.theme.ScreenPadding
import com.surainvestments.roster.ui.theme.StatusColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Full-screen task-completion sheet: manager reference photo (if any), completion report when
 * already done, or camera-only capture + note + submit when not. Mirrors iOS
 * `TaskCompletionDetailSheet` — including the fact that iOS's own fullscreen photo viewer has no
 * pinch-zoom despite what some internal docs claim; see [FullscreenImageViewer].
 */
@Composable
fun TaskCompletionDetailSheet(
    task: RosterTask,
    dateKey: String,
    completion: TaskCompletion?,
    onDismiss: () -> Unit,
    viewModel: TaskCompletionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCompleted = completion?.completed == true

    var capturedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var note by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            scope.launch {
                viewModel.loadBitmap(uri)?.let { capturedImages = capturedImages + it }
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCaptureUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createCaptureUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = ScreenPillTopBarHeight + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RosterCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftTag(
                        text = if (isCompleted) "Completed" else "Pending",
                        tint = if (isCompleted) StatusColors.Approved else StatusColors.Pending,
                    )
                    Text(text = task.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    task.description?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            task.managerPhotoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                RosterCard(onClick = { fullscreenUrl = url }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "REFERENCE / INSTRUCTIONS PHOTO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            if (isCompleted) {
                CompletionReport(task = task, dateKey = dateKey, completion = completion, viewModel = viewModel)
            } else {
                if (completion?.isRedoRequested == true) {
                    Banner(
                        kind = BannerKind.Warning,
                        title = "Redo requested",
                        message = completion.redoReason?.takeIf { it.isNotBlank() },
                    )
                }

                RosterCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (task.photoRequired) "Upload Verification Photo" else "Mark as Done",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        if (task.photoRequired) {
                            if (capturedImages.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    capturedImages.forEachIndexed { index, bitmap ->
                                        CapturedPhotoThumbnail(
                                            bitmap = bitmap,
                                            onDelete = { capturedImages = capturedImages.filterIndexed { i, _ -> i != index } },
                                        )
                                    }
                                }
                            }
                            if (capturedImages.size < 4) {
                                SecondaryButton(
                                    text = if (capturedImages.isEmpty()) "Open Camera" else "Add Another Photo (${capturedImages.size}/4)",
                                    onClick = ::openCamera,
                                )
                            } else {
                                Text(
                                    text = "Photo limit reached (4).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Add a note (optional)") },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                }

                error?.let { Banner(kind = BannerKind.Error, title = it) }

                PrimaryButton(
                    text = if (completion?.isRedoRequested == true) "Resubmit" else "Complete Task",
                    loading = isSubmitting,
                    enabled = !isSubmitting && (!task.photoRequired || capturedImages.isNotEmpty()),
                    onClick = {
                        error = null
                        scope.launch {
                            isSubmitting = true
                            try {
                                viewModel.complete(task, dateKey, capturedImages, note)
                                onDismiss()
                            } catch (e: Exception) {
                                error = e.message ?: "Couldn't submit. Please try again."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                )
            }
        }

        TopEdgeFade(modifier = Modifier.align(Alignment.TopCenter))
        ScreenPillTopBar(title = "Task", onBack = onDismiss, modifier = Modifier.align(Alignment.TopCenter))
    }

    fullscreenUrl?.let { url -> FullscreenImageViewer(url = url, onDismiss = { fullscreenUrl = null }) }
}

@Composable
private fun CompletionReport(task: RosterTask, dateKey: String, completion: TaskCompletion?, viewModel: TaskCompletionViewModel) {
    RosterCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "COMPLETION REPORT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (completion?.completedBy == viewModel.currentUid()) "Completed by you" else "Completed by a teammate",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            completion?.completedAt?.let {
                Text(text = "At ${RosterFormat.time(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            completion?.note?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (task.photoRequired) {
                val localPhotos = remember(task.id, dateKey) { viewModel.localPhotos(task.id, dateKey) }
                if (localPhotos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        localPhotos.forEach { file ->
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier.size(84.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Photo submitted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapturedPhotoThumbnail(bitmap: Bitmap, onDelete: () -> Unit) {
    Box(modifier = Modifier.size(84.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** Plain fit-to-screen image, no pinch-zoom — matches iOS's actual `FullscreenImageView` (not the pinch-zoom the audit doc incorrectly claims exists). */
@Composable
private fun FullscreenImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Done", tint = Color.White)
            }
        }
    }
}

private fun createCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "task_photo_captures").apply { mkdirs() }
    val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
