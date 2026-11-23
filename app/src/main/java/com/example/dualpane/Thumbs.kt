package com.example.dualpane

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Background thumbnail loader for images and videos, with an in-memory cache. */
object Thumbs {
    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val VIDEO = setOf("mp4", "mkv", "3gp", "webm", "avi", "mov")

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun isThumbable(f: File): Boolean {
        if (!f.isFile) return false
        val e = f.extension.lowercase()
        return e in IMAGE || e in VIDEO
    }

    fun load(f: File, target: ImageView, sizePx: Int) {
        val key = f.absolutePath + f.lastModified()
        target.tag = key
        val hit = cache.get(key)
        if (hit != null) {
            target.setImageBitmap(hit)
            target.visibility = View.VISIBLE
            return
        }
        target.visibility = View.INVISIBLE
        pool.execute {
            val bmp = try {
                makeThumb(f, sizePx)
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) {
                cache.put(key, bmp)
                main.post {
                    if (target.tag == key) {
                        target.setImageBitmap(bmp)
                        target.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun makeThumb(f: File, size: Int): Bitmap? {
        val raw = if (f.extension.lowercase() in VIDEO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                r.getFrameAtTime(0)
            } finally {
                r.release()
            }
        } else {
            decodeImage(f, size)
        } ?: return null
        return ThumbnailUtils.extractThumbnail(raw, size, size)
    }

    /** Decodes an image downsampled so it is at most about [max] px on its longer side. */
    fun decodeImage(f: File, max: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var s = 1
        while (bounds.outWidth / s > max * 2 || bounds.outHeight / s > max * 2) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
