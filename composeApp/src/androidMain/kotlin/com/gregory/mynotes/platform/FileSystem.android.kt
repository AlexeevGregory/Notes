package com.gregory.mynotes.platform

import android.content.Context
import java.io.File

fun initFileSystem(context: Context) {
    appContext = context.applicationContext
}

actual fun getAppDataDirectory(): File {
    return File(appContext.filesDir, "mynotes")
}