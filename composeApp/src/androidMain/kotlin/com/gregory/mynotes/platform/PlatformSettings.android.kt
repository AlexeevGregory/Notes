package com.gregory.mynotes.platform

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

lateinit var appContext: Context

fun initPlatform(context: Context) {
    appContext = context.applicationContext
}

actual fun createSettings(): Settings {

    val sharedPreferences =
        appContext.getSharedPreferences(
            "mynotes_settings",
            Context.MODE_PRIVATE
        )

    return SharedPreferencesSettings(sharedPreferences)
}