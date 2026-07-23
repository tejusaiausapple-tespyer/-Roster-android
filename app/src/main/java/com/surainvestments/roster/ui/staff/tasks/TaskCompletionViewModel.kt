package com.surainvestments.roster.ui.staff.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.surainvestments.roster.data.local.TaskPhotoCache
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.TaskCompletionRepository
import com.surainvestments.roster.domain.model.RosterTask
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Backs [TaskCompletionDetailSheet] — photo loading/compression handoff plus the repository write. */
@HiltViewModel
class TaskCompletionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskPhotoCache: TaskPhotoCache,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    fun currentUid(): String? = authRepository.currentUid()

    /** Locally-cached photos already submitted for this task+day (staff's own, never re-fetched from the cloud). */
    fun localPhotos(taskId: String, dateKey: String): List<File> = taskPhotoCache.photos(taskId, dateKey)

    suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
    }

    suspend fun complete(task: RosterTask, dateKey: String, photos: List<Bitmap>, note: String) {
        val uid = checkNotNull(authRepository.currentUid()) { "Not signed in." }
        taskCompletionRepository.completeTask(task, dateKey, uid, photos, note)
    }
}
