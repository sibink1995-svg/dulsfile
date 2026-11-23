package com.example.dualpane

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved day/night choice as soon as the process starts. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val mode = getSharedPreferences("dualpane", MODE_PRIVATE)
            .getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
