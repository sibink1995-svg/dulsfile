package com.example.dualpane

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Calculates total folder sizes in the background and caches them. */
object FolderSizes {
    private val cache = ConcurrentHashMap<String, Long>()
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private fun key(f: File) = f.absolutePath + "|" + f.lastModified()

    fun cached(f: File): Long? = cache[key(f)]

    fun clear() = cache.clear()

    /** Shows the size of [f] in [tv] ("…" until it is known). */
    fun load(f: File, tv: TextView) {
        val k = key(f)
        tv.tag = k
        val hit = cache[k]
        if (hit != null) {
            tv.text = FileOps.humanSize(hit)
            return
        }
        tv.text = "…"
        pool.execute {
            val s = sizeOf(f, 0)
            cache[k] = s
            main.post { if (tv.tag == k) tv.text = FileOps.humanSize(s) }
        }
    }

    fun sizeOf(f: File, depth: Int): Long {
        if (f.isFile) return f.length()
        if (depth > 25) return 0L
        var sum = 0L
        f.listFiles()?.forEach { sum += sizeOf(it, depth + 1) }
        return sum
    }
}
