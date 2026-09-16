package com.surainvestments.roster.data.local

import android.content.Context
import android.graphics.Bitmap
import com.surainvestments.roster.data.service.ImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A signed-in user's own profile photo — app-private storage only, **never** uploaded or synced
 * to Firestore/Storage (this is deliberately a local-only, per-device personalization, not an
 * identity field other users or the backend ever see). Keyed per-uid so switching accounts on a
 * shared device never shows a previous user's photo.
 */
@Singleton
class ProfilePhotoStore @Inject constructor(@ApplicationContext context: Context) {
    private val folder: File = File(context.filesDir, "profile_photos").apply { mkdirs() }

    fun currentFile(uid: String): File? = file(uid).takeIf { it.exists() }

    fun save(uid: String, bitmap: Bitmap) {
        val bytes = ImageCompressor.jpegData(bitmap) ?: return
        FileOutputStream(file(uid)).use { it.write(bytes) }
    }

    fun remove(uid: String) {
        file(uid).delete()
    }

    private fun file(uid: String): File = File(folder, "$uid.jpg")
}
