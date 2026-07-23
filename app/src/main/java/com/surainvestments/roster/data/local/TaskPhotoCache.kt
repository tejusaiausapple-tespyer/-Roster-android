package com.surainvestments.roster.data.local

import android.content.Context
import android.graphics.Bitmap
import com.surainvestments.roster.domain.model.RosterCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device copies of a staff member's own submitted task-completion photos — app-private storage
 * only (never the shared media store), so staff can review what they submitted without a network
 * round-trip. A separate, cruder flat-quality compression from [com.surainvestments.roster.data.service.ImageCompressor]
 * (which sizes the actual upload); this is just a local review copy. Mirrors iOS `TaskPhotoCache`.
 *
 * Local copies age out at the end of the week they were taken — [removePhotosBeforeCurrentWeek]
 * runs once per session (see [com.surainvestments.roster.ui.staff.tasks.StaffTasksViewModel]'s
 * init), not on a background schedule, matching iOS's own "no background sync" precedent.
 */
@Singleton
class TaskPhotoCache @Inject constructor(@ApplicationContext context: Context) {
    private val folder: File = File(context.filesDir, "task_photos").apply { mkdirs() }
    private val dateKeyRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    fun save(bitmap: Bitmap, taskId: String, date: String, index: Int) {
        val file = File(folder, filename(taskId, date, index))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        }
    }

    /** Locally-cached photos for this task+day, in capture order — empty once the retention sweep has run. */
    fun photos(taskId: String, date: String): List<File> =
        (0..3).map { File(folder, filename(taskId, date, it)) }.filter { it.exists() }

    fun removePhotosBeforeCurrentWeek(now: Instant = Instant.now()) {
        val cutoffKey = RosterCalendar.weekStartKey(now)
        val files = folder.listFiles() ?: return
        for (file in files) {
            val key = dateKeyRegex.find(file.name)?.value ?: continue
            if (key < cutoffKey) file.delete()
        }
    }

    private fun filename(taskId: String, date: String, index: Int): String =
        if (index == 0) "${taskId}_$date.jpg" else "${taskId}_${date}_p$index.jpg"
}
