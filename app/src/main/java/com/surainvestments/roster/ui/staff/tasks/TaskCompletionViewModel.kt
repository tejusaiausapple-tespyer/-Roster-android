package com.surainvestments.roster.ui.staff.tasks

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.surainvestments.roster.data.local.TaskPhotoCache
import com.surainvestments.roster.data.repository.AuthRepository
import com.surainvestments.roster.data.repository.TaskCompletionRepository
import com.surainvestments.roster.domain.model.RosterTask
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

/** Backs [TaskCompletionDetailSheet] — photo cache lookup plus the repository write. */
@HiltViewModel
class TaskCompletionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskPhotoCache: TaskPhotoCache,
) : ViewModel() {

    fun currentUid(): String? = authRepository.currentUid()

    /** Locally-cached photos already submitted for this task+day (staff's own, never re-fetched from the cloud). */
    fun localPhotos(taskId: String, dateKey: String): List<File> = taskPhotoCache.photos(taskId, dateKey)

    suspend fun complete(task: RosterTask, dateKey: String, photos: List<Bitmap>, note: String) {
        val uid = checkNotNull(authRepository.currentUid()) { "Not signed in." }
        taskCompletionRepository.completeTask(task, dateKey, uid, photos, note)
    }
}
