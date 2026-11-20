package com.ibfavas.flick.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Carry a small thumbnail with the contact so icons survive backups and transient picker grants. */
object ContactPhoto {
    private const val SIZE = 128
    fun read(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options()
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > SIZE * 2)
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val edge = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width-edge)/2, (bitmap.height-edge)/2, edge, edge)
            val thumb = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            val bytes = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun decode(payload: String): Bitmap? {
        val data = payload.split('\n', limit = 3).getOrNull(2) ?: return null
        if (data.length > 64000) return null
        return runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..SIZE || bounds.outHeight !in 1..SIZE) null
            else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
