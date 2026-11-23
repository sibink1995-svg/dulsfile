package com.example.dualpane

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DateFormat
import java.util.Date

class FileAdapter(
    private val selected: Set<File>,
    private val isExpanded: (File) -> Boolean,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit,
    private val onIconClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private var items: List<Node> = emptyList()
    var showFullPath = false
    private val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun submit(list: List<Node>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val arrow: TextView = v.findViewById(R.id.arrow)
        val icon: TextView = v.findViewById(R.id.icon)
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val name: TextView = v.findViewById(R.id.name)
        val info: TextView = v.findViewById(R.id.info)
        val size: TextView = v.findViewById(R.id.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val node = items[position]
        val f = node.file
        val d = h.itemView.resources.displayMetrics.density

        // indentation = depth in the tree
        h.itemView.setPadding(((6 + node.depth * 18) * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())

        h.arrow.text = when {
            !f.isDirectory -> ""
            isExpanded(f) -> "▼"
            else -> "▶"
        }

        h.icon.text = iconFor(f)
        if (Thumbs.isThumbable(f)) {
            Thumbs.load(f, h.thumb, (44 * d).toInt())
        } else {
            h.thumb.tag = null
            h.thumb.visibility = View.GONE
        }

        if (f.isDirectory) {
            h.icon.setOnClickListener { onIconClick(f) }
            h.icon.setOnLongClickListener { onLongClick(f); true }
        } else {
            h.icon.setOnClickListener(null)
            h.icon.setOnLongClickListener(null)
            h.icon.isClickable = false
            h.icon.isLongClickable = false
        }

        h.name.text = f.name
        val date = df.format(Date(f.lastModified()))
        h.info.text = when {
            showFullPath -> f.parent ?: ""
            f.isDirectory -> "${f.list()?.size ?: 0} items  •  $date"
            else -> date
        }
        if (f.isDirectory) {
            FolderSizes.load(f, h.size)
        } else {
            h.size.tag = null
            h.size.text = FileOps.humanSize(f.length())
        }

        h.itemView.setBackgroundColor(
            if (f in selected) h.itemView.context.getColor(R.color.selected) else Color.TRANSPARENT
        )
        h.itemView.setOnClickListener { onClick(f) }
        h.itemView.setOnLongClickListener { onLongClick(f); true }
    }

    private fun iconFor(f: File): String {
        if (f.isDirectory) return "📁"
        return when (f.extension.lowercase()) {
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "🎬"
            "mp3", "wav", "flac", "m4a", "ogg", "aac" -> "🎵"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "🖼"
            "zip", "rar", "7z", "tar", "gz" -> "🗜"
            "pdf" -> "📕"
            "apk" -> "📦"
            else -> "📄"
        }
    }
}
