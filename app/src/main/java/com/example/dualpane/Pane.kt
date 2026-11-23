package com.example.dualpane

import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

enum class SortBy { NAME, SIZE, DATE }

/** One visible row of the tree: a file/folder and how deep it is nested. */
class Node(val file: File, val depth: Int)

/**
 * One of the two file panels, shown as a tree: tapping a folder expands it
 * downwards (indented) instead of replacing the list.
 *
 *  - [dir]      = root of the tree (changed with the folder icon / Up dir)
 *  - [focusDir] = folder last tapped; the target for Copy/Move/New folder etc.
 */
class Pane(
    root: View,
    private val onFocus: (Pane) -> Unit,
    private val onOpenFile: (File) -> Unit
) {
    private val pathView: TextView = root.findViewById(R.id.path)
    private val list: RecyclerView = root.findViewById(R.id.list)

    val storageRoot: File = Environment.getExternalStorageDirectory()
    var dir: File = storageRoot
        private set
    var focusDir: File = storageRoot
        private set
    private val expanded = HashSet<String>()
    val selected = linkedSetOf<File>()

    /** Files currently listed, in display order. */
    var shown: List<File> = emptyList()
        private set

    var showHidden = false
    var sortBy = SortBy.NAME

    /** Called every time the pane root changes (used for History). */
    var onNavigate: ((File) -> Unit)? = null

    /** Non-null while showing search results instead of the tree. */
    private var searchResults: List<File>? = null
    private var searchTitle: String? = null

    private val adapter = FileAdapter(selected, ::isExpanded, ::onItemClick, ::onItemLongClick, ::onIconClick)

    init {
        list.layoutManager = LinearLayoutManager(root.context)
        list.adapter = adapter
        pathView.setOnClickListener { onFocus(this) }
        list.setOnTouchListener { _, _ -> onFocus(this); false }
    }

    fun setActive(active: Boolean) {
        pathView.setBackgroundColor(
            pathView.context.getColor(if (active) R.color.pane_bar_active else R.color.pane_bar)
        )
    }

    private fun isExpanded(f: File) = f.absolutePath in expanded

    /** Makes [target] the new root of the tree. */
    fun navigate(target: File) {
        if (!target.isDirectory) return
        dir = target
        focusDir = target
        expanded.clear()
        searchResults = null
        searchTitle = null
        selected.clear()
        refresh()
        onNavigate?.invoke(target)
    }

    /**
     * Closes the focused folder / goes one level up.
     * @return false if already at the top and nothing happened.
     */
    fun up(): Boolean {
        if (searchResults != null) {
            searchResults = null
            searchTitle = null
            selected.clear()
            refresh()
            return true
        }
        if (focusDir.absolutePath != dir.absolutePath) {
            expanded.remove(focusDir.absolutePath)
            val parent = focusDir.parentFile
            focusDir = if (parent != null && parent.absolutePath.startsWith(dir.absolutePath)) parent else dir
            refresh()
            return true
        }
        val parent = dir.parentFile ?: return false
        if (dir.absolutePath == storageRoot.absolutePath || !parent.canRead()) return false
        navigate(parent)
        return true
    }

    fun showSearch(results: List<File>, query: String) {
        searchResults = results
        searchTitle = "🔍 \"$query\" (${results.size})"
        selected.clear()
        refresh()
    }

    private fun sortFiles(files: List<File>): List<File> {
        val visible = files.filter { showHidden || !it.name.startsWith(".") }
        val cmp: Comparator<File> = when (sortBy) {
            SortBy.NAME -> compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.SIZE -> compareByDescending<File> { if (it.isDirectory) FolderSizes.cached(it) ?: 0L else it.length() }
            SortBy.DATE -> compareByDescending<File> { it.lastModified() }
        }
        return visible.sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
    }

    private fun children(d: File): List<File> = sortFiles(d.listFiles()?.toList() ?: emptyList())

    private fun addChildren(d: File, depth: Int, out: MutableList<Node>) {
        for (c in children(d)) {
            out.add(Node(c, depth))
            if (c.isDirectory && depth < 20 && isExpanded(c)) addChildren(c, depth + 1, out)
        }
    }

    fun refresh() {
        val sr = searchResults
        val nodes = ArrayList<Node>()
        if (sr != null) sortFiles(sr).forEach { nodes.add(Node(it, 0)) } else addChildren(dir, 0, nodes)
        shown = nodes.map { it.file }
        adapter.showFullPath = sr != null
        adapter.submit(nodes)
        pathView.text = searchTitle ?: focusDir.absolutePath
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(searchResults?.let { sortFiles(it) } ?: children(focusDir))
        adapter.notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        adapter.notifyDataSetChanged()
    }

    private fun toggleExpand(f: File) {
        if (!expanded.remove(f.absolutePath)) expanded.add(f.absolutePath)
        focusDir = f
        refresh()
    }

    private fun onItemClick(f: File) {
        onFocus(this)
        when {
            selected.isNotEmpty() -> toggle(f)
            f.isDirectory && searchResults != null -> navigate(f)
            f.isDirectory -> toggleExpand(f)
            else -> {
                if (searchResults == null) {
                    focusDir = f.parentFile ?: focusDir
                    pathView.text = focusDir.absolutePath
                }
                onOpenFile(f)
            }
        }
    }

    /** Tapping a folder's icon makes it the new root of the tree. */
    private fun onIconClick(f: File) {
        onFocus(this)
        if (f.isDirectory) navigate(f)
    }

    private fun onItemLongClick(f: File) {
        onFocus(this)
        toggle(f)
    }

    private fun toggle(f: File) {
        if (!selected.add(f)) selected.remove(f)
        adapter.notifyDataSetChanged()
    }
}
