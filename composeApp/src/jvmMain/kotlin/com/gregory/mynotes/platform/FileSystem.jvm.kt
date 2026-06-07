package com.gregory.mynotes.platform

import java.io.File

actual fun getAppDataDirectory(): File {
    return File(
        System.getProperty("user.home"),
        ".mynotes"
    )
}