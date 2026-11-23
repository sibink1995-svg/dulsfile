package com.example.dualpane

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import kotlin.concurrent.thread

/** Built-in viewer: images (swipe + pinch zoom), text files and PDFs. */
class ViewerActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private lateinit var header: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(intent.getStringExtra("path") ?: return finish())
        val type = intent.getStringExtra("type") ?: "text"

        header = TextView(this).apply {
            text = file.name
            setPadding(24, 20, 24, 20)
            setBackgroundColor(getColor(R.color.pane_bar_active))
            setSingleLine()
        }
        val content: View = try {
            when (type) {
                "image" -> pagerView(file)
                "pdf" -> pdfView(file)
                else -> textView(file)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_LONG).show()
            TextView(this)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    // ------------------------------------------------------------ images (swipe)

    private class ImageHolder(val iv: ZoomImageView) : RecyclerView.ViewHolder(iv)

    private fun pagerView(file: File): View {
        val paths = intent.getStringArrayExtra("paths")?.toList() ?: listOf(file.absolutePath)
        val start = paths.indexOf(file.absolutePath).coerceAtLeast(0)

        val pager = ViewPager2(this)
        pager.adapter = object : RecyclerView.Adapter<ImageHolder>() {
            override fun getItemCount() = paths.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
                val iv = ZoomImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.BLACK)
                }
                return ImageHolder(iv)
            }

            override fun onBindViewHolder(h: ImageHolder, position: Int) {
                val p = paths[position]
                h.iv.reset()
                h.iv.setImageDrawable(null)
                h.iv.tag = p
                thread {
                    val bmp = Thumbs.decodeImage(File(p), 2048)
                    runOnUiThread { if (h.iv.tag == p) h.iv.setImageBitmap(bmp) }
                }
            }
        }
        pager.setCurrentItem(start, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                header.text = "${File(paths[position]).name}  (${position + 1}/${paths.size})"
            }
        })
        header.text = "${File(paths[start]).name}  (${start + 1}/${paths.size})"
        return pager
    }

    // ------------------------------------------------------------ text

    private fun textView(file: File): View {
        val text = file.reader().use { r ->
            val buf = CharArray(500_000)
            val n = r.read(buf)
            String(buf, 0, maxOf(n, 0))
        }
        val tv = TextView(this).apply {
            this.text = text + if (file.length() > 500_000) "\n\n… (truncated)" else ""
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(24, 16, 24, 16)
            setTextIsSelectable(true)
        }
        return ScrollView(this).apply { addView(tv) }
    }

    // ------------------------------------------------------------ pdf

    private fun pdfView(file: File): View {
        val pdf = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        renderer = pdf
        val width = resources.displayMetrics.widthPixels
        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = pdf.pageCount
            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val iv = ImageView(p.context).apply {
                    adjustViewBounds = true
                    layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = 12 }
                }
                return object : RecyclerView.ViewHolder(iv) {}
            }

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                synchronized(pdf) {
                    val page = pdf.openPage(pos)
                    val bmp = Bitmap.createBitmap(width, width * page.height / page.width, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    (h.itemView as ImageView).setImageBitmap(bmp)
                }
            }
        }
        return rv
    }

    override fun onDestroy() {
        renderer?.close()
        super.onDestroy()
    }
}
