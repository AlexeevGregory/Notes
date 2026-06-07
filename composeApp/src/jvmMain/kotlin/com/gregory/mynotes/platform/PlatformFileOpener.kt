package com.gregory.mynotes.platform

import java.awt.Desktop
import java.io.File

actual fun openFile(filePath: String) {
    val file = File(filePath)
    if (file.exists() && Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
    } else {
        println("File not found or Desktop not supported: $filePath")
    }
}