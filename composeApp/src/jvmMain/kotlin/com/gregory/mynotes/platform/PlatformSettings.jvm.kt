package com.gregory.mynotes.platform

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

actual fun createSettings(): Settings {

    val file = File(
        System.getProperty("user.home"),
        ".mynotes/session.properties"
    )
    file.parentFile.mkdirs()

    val properties = Properties()

    if (file.exists()) {
        FileInputStream(file).use {
            properties.load(it)
        }
    }

    return PropertiesSettings(
        properties,
        { FileOutputStream(file).use { properties.store(it, null) } }
    )
}