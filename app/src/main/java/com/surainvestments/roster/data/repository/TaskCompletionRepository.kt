package com.surainvestments.roster.data.repository

import android.graphics.Bitmap
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.surainvestments.roster.data.di.ApplicationScope
import com.surainvestments.roster.data.local.TaskPhotoCache
import com.surainvestments.roster.data.service.ImageCompressor
import com.surainvestments.roster.domain.model.RosterTask
import com.surainvestments.roster.domain.model.TaskCompletion
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/**
 * `task_completions` collection — the Android analogue of iOS `RosterRepository`'s
 * `taskCompletions` listener + `completeTask`. Completion doc id is `{taskId}_{date}`: **one
 * completion shared across every staff member assigned to that task on that day**, not per-staff
 * — whoever completes it first "wins" and it becomes visible/attributed to them.
 */
@Singleton
class TaskCompletionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val taskPhotoCache: TaskPhotoCache,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val dayCache = ConcurrentHashMap<String, StateFlow<List<TaskCompletion>>>()
    private val weekCache = ConcurrentHashMap<String, StateFlow<List<TaskCompletion>>>()

    /** Live list of every task completion logged on [dateKey] (yyyy-MM-dd), any staff member. */
    fun completionsForDate(dateKey: String): Flow<List<TaskCompletion>> = callbackFlow {
        val registration = firestore.collection("task_completions")
            .whereEqualTo("date", dateKey)
            .addSnapshotListener { snapshot, _ ->
                val completions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { TaskCompletion.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(completions)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [completionsForDate] — one listener per day across every collector. */
    fun sharedCompletionsForDate(dateKey: String): StateFlow<List<TaskCompletion>> =
        dayCache.getOrPut(dateKey) {
            completionsForDate(dateKey).stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /** Live completions across a bounded set of dates (e.g. the visible week) — for the week-strip's marked-day dots. */
    fun completionsForDates(dateKeys: List<String>): Flow<List<TaskCompletion>> = callbackFlow {
        if (dateKeys.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = firestore.collection("task_completions")
            .whereIn("date", dateKeys)
            .addSnapshotListener { snapshot, _ ->
                val completions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { TaskCompletion.fromDocument(doc.id, it) }
                } ?: emptyList()
                trySend(completions)
            }
        awaitClose { registration.remove() }
    }

    /** Shared, cached version of [completionsForDates], keyed by the week's Monday. */
    fun sharedCompletionsForWeek(weekKey: String, dateKeys: List<String>): StateFlow<List<TaskCompletion>> =
        weekCache.getOrPut(weekKey) {
            completionsForDates(dateKeys).stateIn(appScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    /**
     * Complete (or resubmit) [task] for [dateKey]: uploads up to 4 [photos] to Storage, caches
     * local review copies, then **full-overwrites** `task_completions/{taskId}_{dateKey}` — never
     * a merge, matching iOS's `setData` exactly, since a resubmission must also clear any prior
     * `status`/`redoReason`/manager-review fields, not just add the new completion fields on top.
     */
    suspend fun completeTask(task: RosterTask, dateKey: String, staffId: String, photos: List<Bitmap>, note: String?) {
        val urls = mutableListOf<String>()
        photos.take(4).forEachIndexed { index, bitmap ->
            val bytes = ImageCompressor.jpegData(bitmap)
                ?: error("Couldn't compress photo ${index + 1} to fit the upload size limit.")
            val ref = storage.reference.child("task_photos/$staffId/${task.id}_${dateKey}_${UUID.randomUUID()}.jpg")
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("owner", staffId)
                .setCustomMetadata("taskId", task.id)
                .setCustomMetadata("date", dateKey)
                .build()
            ref.putBytes(bytes, metadata).await()
            urls += ref.downloadUrl.await().toString()
            taskPhotoCache.save(bitmap, task.id, dateKey, index)
        }

        val data = mutableMapOf<String, Any>(
            "taskId" to task.id,
            "date" to dateKey,
            "completed" to true,
            "completedAt" to FieldValue.serverTimestamp(),
            "completedBy" to staffId,
        )
        if (urls.isNotEmpty()) {
            data["staffPhotoUrls"] = urls
            data["staffPhotoUrl"] = urls.first()
        }
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { data["note"] = it }

        firestore.collection("task_completions").document("${task.id}_$dateKey").set(data).await()
    }
}
