package com.example.dualpane

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var left: Pane
    private lateinit var right: Pane
    private lateinit var active: Pane
    private val other: Pane get() = if (active === left) right else left
    private var panesReady = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        restored = savedInstanceState
        setContentView(R.layout.activity_main)

        left = Pane(findViewById(R.id.paneLeft), ::setActive, ::openFile)
        right = Pane(findViewById(R.id.paneRight), ::setActive, ::openFile)
        left.onNavigate = ::recordHistory
        right.onNavigate = ::recordHistory
        active = left
        setActive(left)

        buildToolbar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!active.up()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateStorage()
        if (hasStorageAccess()) {
            if (!panesReady) {
                panesReady = true
                val r = restored
                val l0 = r?.getString("l")?.let { File(it) }?.takeIf { it.isDirectory }
                val r0 = r?.getString("r")?.let { File(it) }?.takeIf { it.isDirectory }
                left.navigate(l0 ?: left.storageRoot)
                right.navigate(
                    r0 ?: File(left.storageRoot, "Download").takeIf { it.isDirectory } ?: right.storageRoot
                )
                if (r?.getBoolean("rightActive") == true) setActive(right)
                restored = null
            } else {
                left.refresh(); right.refresh()
            }
        } else {
            requestStorageAccess()
        }
    }

    // ---------------------------------------------------------------- day / night

    private var restored: android.os.Bundle? = null

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        if (::left.isInitialized && ::right.isInitialized) {
            outState.putString("l", left.dir.absolutePath)
            outState.putString("r", right.dir.absolutePath)
            outState.putBoolean("rightActive", active === right)
        }
    }

    private fun toggleNightMode() {
        val isNight = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val mode = if (isNight) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        getSharedPreferences("dualpane", MODE_PRIVATE).edit().putInt("nightMode", mode).apply()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode) // recreates the activity
    }

    // ---------------------------------------------------------------- storage bar

    private fun updateStorage() {
        try {
            val st = android.os.StatFs(Environment.getExternalStorageDirectory().path)
            val total = st.totalBytes
            val free = st.availableBytes
            val used = total - free
            val pct = if (total > 0) (used * 100 / total).toInt() else 0
            findViewById<TextView>(R.id.storageText).text =
                "Internal storage:  Used ${FileOps.humanSize(used)} / ${FileOps.humanSize(total)}" +
                        "  •  Free ${FileOps.humanSize(free)}  ($pct% used)"
            findViewById<android.widget.ProgressBar>(R.id.storageBar).progress = pct
        } catch (e: Exception) {
            // ignore: bar just keeps its previous value
        }
    }

    // ---------------------------------------------------------------- permissions

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            AlertDialog.Builder(this)
                .setTitle("Storage access needed")
                .setMessage("Allow \"All files access\" so the app can browse and manage your files.")
                .setCancelable(false)
                .setPositiveButton("Open settings") { _, _ ->
                    val i = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(i)
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasStorageAccess()) onResume()
    }

    // ---------------------------------------------------------------- toolbar

    private class Btn(val icon: String, val label: String, val action: () -> Unit)

    private fun buildToolbar() {
        val buttons = listOf(
            Btn("⬆️", "Up dir") { active.up() },
            Btn("📊", "Disk map") {
                startActivity(Intent(this, DiskMapActivity::class.java).putExtra("path", active.focusDir.absolutePath))
            },
            Btn("📋", "Copy") { copyOrMove(move = false) },
            Btn("✂️", "Move") { copyOrMove(move = true) },
            Btn("✏️", "Rename") { rename() },
            Btn("📤", "Extract") { extractSelected() },
            Btn("⭐", "Favorites") { favoritesDialog() },
            Btn("🕒", "History") { historyDialog() },
            Btn("📱", "Apps") { startActivity(Intent(this, AppsActivity::class.java)) },
            Btn("🗑️", "Delete") { delete() },
            Btn("🗜", "Create ZIP") { createZip() },
            Btn("📁", "New folder") { newItem(folder = true) },
            Btn("📄", "New text file") { newItem(folder = false) },
            Btn("☑️", "Select all") { active.selectAll() },
            Btn("👁️", "Show hidden") { toggleHidden() },
            Btn("↕️", "Sort by") { sortDialog() },
            Btn("🔍", "Find") { findDialog() },
            Btn("🌓", "Day / Night") { toggleNightMode() },
            Btn("🔄", "Refresh") { FolderSizes.clear(); updateStorage(); left.refresh(); right.refresh() }
        )
        val bar = findViewById<LinearLayout>(R.id.toolbar)
        val inflater = LayoutInflater.from(this)
        for (b in buttons) {
            val v = inflater.inflate(R.layout.toolbar_button, bar, false)
            v.findViewById<TextView>(R.id.btnIcon).text = b.icon
            v.findViewById<TextView>(R.id.btnLabel).text = b.label
            v.setOnClickListener { b.action() }
            bar.addView(v)
        }
    }

    private fun setActive(p: Pane) {
        active = p
        left.setActive(p === left)
        right.setActive(p === right)
    }

    // ---------------------------------------------------------------- actions

    private fun selection(): List<File> = active.selected.toList()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requireSelection(): List<File>? {
        val s = selection()
        if (s.isEmpty()) toast("Long-press files to select them first")
        return s.ifEmpty { null }
    }

    /** Runs [work] off the main thread, then refreshes both panes. */
    private fun background(msg: String, work: () -> String) {
        toast(msg)
        thread {
            val result = work()
            runOnUiThread {
                FolderSizes.clear()
                updateStorage()
                active.clearSelection()
                left.refresh(); right.refresh()
                toast(result)
            }
        }
    }

    private fun copyOrMove(move: Boolean) {
        val files = requireSelection() ?: return
        val dest = other.focusDir
        background(if (move) "Moving…" else "Copying…") {
            val ok = files.count { if (move) FileOps.move(it, dest) else FileOps.copy(it, dest) }
            "${if (move) "Moved" else "Copied"} $ok/${files.size} to ${dest.name}"
        }
    }

    private fun delete() {
        val files = requireSelection() ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete ${files.size} item(s)?")
            .setMessage(files.joinToString("\n") { it.name }.take(400))
            .setPositiveButton("Delete") { _, _ ->
                background("Deleting…") {
                    val ok = files.count { FileOps.delete(it) }
                    "Deleted $ok/${files.size}"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rename() {
        val files = requireSelection() ?: return
        if (files.size != 1) return batchRename(files)
        val f = files[0]
        prompt("Rename", f.name) { name ->
            toast(if (FileOps.rename(f, name)) "Renamed" else "Rename failed")
            active.clearSelection()
            active.refresh()
        }
    }

    private fun newItem(folder: Boolean) {
        val dir = active.focusDir
        prompt(if (folder) "New folder" else "New text file", if (folder) "New folder" else "note.txt") { name ->
            val f = File(dir, name)
            val ok = try {
                if (name.isBlank() || f.exists()) false
                else if (folder) f.mkdirs() else f.createNewFile()
            } catch (e: Exception) {
                false
            }
            toast(if (ok) "Created" else "Could not create")
            active.refresh()
        }
    }

    private fun createZip() {
        val files = requireSelection() ?: return
        val dir = active.focusDir
        val base = if (files.size == 1) files[0].nameWithoutExtension else "archive"
        prompt("Create ZIP", "$base.zip") { name ->
            val out = FileOps.uniqueTarget(dir, if (name.endsWith(".zip")) name else "$name.zip")
            background("Zipping…") {
                if (FileOps.zip(files, out)) "Created ${out.name}" else "ZIP failed"
            }
        }
    }

    private fun toggleHidden() {
        val v = !active.showHidden
        left.showHidden = v; right.showHidden = v
        left.refresh(); right.refresh()
        toast(if (v) "Hidden files shown" else "Hidden files hidden")
    }

    private fun sortDialog() {
        val names = arrayOf("Name", "Size (largest first)", "Date (newest first)")
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setSingleChoiceItems(names, active.sortBy.ordinal) { d, which ->
                val s = SortBy.values()[which]
                left.sortBy = s; right.sortBy = s
                left.refresh(); right.refresh()
                d.dismiss()
            }
            .show()
    }

    private fun findDialog() {
        val dir = active.focusDir
        prompt("Find in ${dir.name}", "") { q ->
            if (q.isBlank()) return@prompt
            val pane = active
            toast("Searching…")
            thread {
                val res = FileOps.search(dir, q)
                runOnUiThread { pane.showSearch(res, q) }
            }
        }
    }

    // ---------------------------------------------------------------- open / viewers

    private val textExt = setOf(
        "txt", "log", "md", "json", "xml", "csv", "html", "htm", "css", "js", "kt", "java",
        "py", "yml", "yaml", "ini", "conf", "properties", "sh", "gradle", "kts", "srt"
    )

    private fun openFile(f: File) {
        val ext = f.extension.lowercase()
        fun viewer(type: String) = startActivity(
            Intent(this, ViewerActivity::class.java).putExtra("path", f.absolutePath).putExtra("type", type)
        )
        when {
            ext in Thumbs.IMAGE -> {
                val imgs = active.shown.filter { it.parentFile == f.parentFile && it.extension.lowercase() in Thumbs.IMAGE }.map { it.absolutePath }
                val idx = imgs.indexOf(f.absolutePath).coerceAtLeast(0)
                val window = imgs.drop(maxOf(0, idx - 250)).take(500) // keep the Intent small
                startActivity(
                    Intent(this, ViewerActivity::class.java)
                        .putExtra("path", f.absolutePath)
                        .putExtra("type", "image")
                        .putExtra("paths", window.toTypedArray())
                )
            }
            ext == "pdf" -> viewer("pdf")
            ext in textExt -> viewer("text")
            ext == "zip" -> zipDialog(f)
            else -> FileOps.open(this, f)
        }
    }

    private fun zipDialog(zip: File) {
        val dir = zip.parentFile ?: return
        AlertDialog.Builder(this)
            .setTitle(zip.name)
            .setItems(
                arrayOf("Extract here (new folder)", "Extract to other pane", "List contents", "Open with…")
            ) { _, which ->
                when (which) {
                    0 -> extract(zip, dir)
                    1 -> extract(zip, other.focusDir)
                    2 -> {
                        val names = try {
                            FileOps.listZip(zip)
                        } catch (e: Exception) {
                            listOf("Cannot read archive")
                        }
                        AlertDialog.Builder(this)
                            .setTitle("${zip.name} (${names.size})")
                            .setItems(names.take(500).toTypedArray(), null)
                            .setPositiveButton("Close", null)
                            .show()
                    }
                    3 -> FileOps.open(this, zip)
                }
            }
            .show()
    }

    private fun extract(zip: File, parent: File) {
        val dest = FileOps.uniqueTarget(parent, zip.nameWithoutExtension)
        background("Extracting…") {
            if (FileOps.unzip(zip, dest)) "Extracted to ${dest.name}" else "Extract failed"
        }
    }

    private fun extractSelected() {
        val zips = (requireSelection() ?: return).filter { it.extension.equals("zip", true) }
        if (zips.isEmpty()) return toast("Select a .zip file")
        val parent = active.focusDir
        background("Extracting…") {
            val ok = zips.count { FileOps.unzip(it, FileOps.uniqueTarget(parent, it.nameWithoutExtension)) }
            "Extracted $ok/${zips.size}"
        }
    }

    // ---------------------------------------------------------------- batch rename

    private fun batchRename(files: List<File>) {
        prompt("Batch rename (use {n} for number)", "File {n}") { pattern ->
            if (!pattern.contains("{n}")) return@prompt toast("Pattern must contain {n}")
            val sorted = files.sortedBy { it.name.lowercase() }
            var ok = 0
            sorted.forEachIndexed { i, f ->
                val ext = if (f.isFile && f.extension.isNotEmpty()) "." + f.extension else ""
                val num = (i + 1).toString().padStart(2, '0')
                if (FileOps.rename(f, pattern.replace("{n}", num) + ext)) ok++
            }
            toast("Renamed $ok/${files.size}")
            active.clearSelection()
            left.refresh(); right.refresh()
        }
    }

    // ---------------------------------------------------------------- favorites & history

    private val prefs by lazy { getSharedPreferences("dualpane", MODE_PRIVATE) }

    private fun getList(key: String): MutableList<String> =
        (prefs.getString(key, "") ?: "").split('\n').filter { it.isNotBlank() }.toMutableList()

    private fun putList(key: String, list: List<String>) =
        prefs.edit().putString(key, list.joinToString("\n")).apply()

    private fun recordHistory(dir: File) {
        val h = getList("history")
        h.remove(dir.absolutePath)
        h.add(0, dir.absolutePath)
        putList("history", h.take(30))
    }

    private fun goTo(path: String) {
        val f = File(path)
        if (f.isDirectory) active.navigate(f) else toast("Folder not found")
    }

    private fun favoritesDialog() {
        val favs = getList("fav")
        val items = (listOf("⭐ Add current folder") + favs).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Favorites")
            .setItems(items) { _, i ->
                if (i == 0) {
                    val p = active.focusDir.absolutePath
                    if (p !in favs) putList("fav", favs + p)
                    toast("Added")
                } else {
                    goTo(favs[i - 1])
                }
            }
            .setNegativeButton("Remove…") { _, _ ->
                if (favs.isEmpty()) return@setNegativeButton
                AlertDialog.Builder(this)
                    .setTitle("Remove favorite")
                    .setItems(favs.toTypedArray()) { _, i ->
                        putList("fav", favs.filterIndexed { idx, _ -> idx != i })
                    }
                    .show()
            }
            .show()
    }

    private fun historyDialog() {
        val h = getList("history")
        if (h.isEmpty()) return toast("No history yet")
        AlertDialog.Builder(this)
            .setTitle("History")
            .setItems(h.toTypedArray()) { _, i -> goTo(h[i]) }
            .setNegativeButton("Clear") { _, _ -> putList("history", emptyList()) }
            .show()
    }

    private fun prompt(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
            setSelection(text.length)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
