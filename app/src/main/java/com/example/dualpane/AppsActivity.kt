package com.example.dualpane

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

/** Simple app manager: open, back up APK, app info, uninstall. */
class AppsActivity : AppCompatActivity() {

    private class App(val label: String, val pkg: String, val apk: File, val system: Boolean)

    private var all: List<App> = emptyList()
    private var shown: List<App> = emptyList()
    private var showSystem = false
    private lateinit var header: TextView
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        header = TextView(this).apply {
            setPadding(24, 20, 24, 20)
            setBackgroundColor(getColor(R.color.pane_bar_active))
            setOnClickListener {
                showSystem = !showSystem
                render()
            }
        }
        list = ListView(this)
        list.setOnItemClickListener { _, _, i, _ -> actions(shown[i]) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        header.text = "Loading apps…"
        thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0).map {
                App(
                    pm.getApplicationLabel(it).toString(), it.packageName, File(it.sourceDir),
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                all = apps
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // an app may have been uninstalled
        if (all.isNotEmpty()) {
            all = all.filter { packageManagerHas(it.pkg) }
            render()
        }
    }

    private fun packageManagerHas(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) {
        false
    }

    private fun render() {
        shown = all.filter { showSystem || !it.system }
        header.text = "${shown.size} apps  •  tap here to ${if (showSystem) "hide" else "show"} system apps"
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            shown.map { "${it.label}\n${it.pkg}  •  ${FileOps.humanSize(it.apk.length())}" }
        )
    }

    private fun actions(a: App) {
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(arrayOf("Open", "Backup APK", "App info", "Uninstall")) { _, which ->
                when (which) {
                    0 -> packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it) }
                        ?: Toast.makeText(this, "No launcher activity", Toast.LENGTH_SHORT).show()
                    1 -> backup(a)
                    2 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                    )
                    3 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                }
            }
            .show()
    }

    private fun backup(a: App) {
        val dir = File(Environment.getExternalStorageDirectory(), "Download/AppBackups")
        thread {
            dir.mkdirs()
            val out = FileOps.uniqueTarget(dir, "${a.label}_${a.pkg}.apk".replace('/', '_'))
            val ok = try {
                a.apk.copyTo(out); true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Saved to Download/AppBackups" else "Backup failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
