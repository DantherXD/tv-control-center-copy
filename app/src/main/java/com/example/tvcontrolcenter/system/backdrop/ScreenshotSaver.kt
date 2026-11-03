package com.example.tvcontrolcenter.system.backdrop

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshot button. Android TV ignores the system "take screenshot" action, so this takes the
 * picture itself through the accessibility service and saves it to Pictures/Screenshots.
 */
object ScreenshotSaver {
    /** True when a picture was saved. */
    suspend fun takeAndSave(context: Context): Boolean {
        val shot = BackdropCapture.screenshotFull() ?: return false
        return try {
            withContext(Dispatchers.IO) { save(context, shot) }
        } finally {
            shot.recycle()
        }
    }

    private fun save(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_$stamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val ok = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (!ok) resolver.delete(uri, null, null)
            ok
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
