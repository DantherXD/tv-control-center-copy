package com.example.tvcontrolcenter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** The profile picture: one square 256 px PNG in the app's private files. */
object ProfilePhoto {
    private const val SIZE = 256

    private fun file(context: Context) = File(context.filesDir, "profile.png")

    fun load(context: Context): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(file(context).path)?.asImageBitmap() }.getOrNull()

    /** Reads the picture, crops the centre square, shrinks it and saves it. False if it could not be read. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        file(context).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
    }
}
