package com.surainvestments.roster.data.service

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Downscale-then-quality-step JPEG compression for task-completion proof photos. Mirrors iOS
 * `ImageCompressor` (`Services/ImageCompressor.swift`): downscale the longest edge to
 * [maxDimension]px, then try JPEG quality 70→55→40→25→10 until the result fits [maxBytes]; if
 * still too big, halve the dimensions once more at quality 10 as a last resort.
 */
object ImageCompressor {
    const val maxBytes: Int = 2 * 1024 * 1024
    const val maxDimension: Int = 1600

    fun jpegData(bitmap: Bitmap, maxBytes: Int = ImageCompressor.maxBytes): ByteArray? {
        val scaled = downscale(bitmap, maxDimension)
        var quality = 70
        while (quality >= 10) {
            val bytes = toJpeg(scaled, quality)
            if (bytes.size <= maxBytes) return bytes
            quality -= 15
        }
        val tiny = downscale(scaled, maxDimension / 2)
        val bytes = toJpeg(tiny, 10)
        return bytes.takeIf { it.size <= maxBytes }
    }

    private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
