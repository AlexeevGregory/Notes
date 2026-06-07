package com.gregory.mynotes.platform

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.content.ContentResolver
import android.webkit.MimeTypeMap

actual fun openFile(filePath: String) {
    val context = AndroidContext.applicationContext
    var finalUri: Uri? = null

    when {
        filePath.startsWith("content://") -> {
            finalUri = Uri.parse(filePath)
        }
        filePath.startsWith("/") -> {
            // 1. Восстановление для изображений (уже было)
            val imagePattern = Regex("/document/image:(\\d+)")
            val imageMatch = imagePattern.find(filePath)
            if (imageMatch != null) {
                val imageId = imageMatch.groupValues[1]
                finalUri = Uri.parse("content://media/external/images/media/$imageId")
                println("Recovered image URI: $finalUri")
            } else {
                // 2. Восстановление для любых документов (например, /document/7357)
                val docPattern = Regex("/document/(\\d+)")
                val docMatch = docPattern.find(filePath)
                if (docMatch != null) {
                    val docId = docMatch.groupValues[1]
                    val possibleAuthorities = listOf(
                        "com.android.providers.downloads.documents",
                        "com.android.externalstorage.documents",
                        "com.android.providers.media.documents"
                    )
                    for (authority in possibleAuthorities) {
                        val testUri = Uri.parse("content://$authority/document/$docId")
                        try {
                            context.contentResolver.openInputStream(testUri)?.close()
                            finalUri = testUri
                            println("Recovered document URI: $finalUri")
                            break
                        } catch (e: Exception) {
                            // не удалось, пробуем следующий
                        }
                    }
                    if (finalUri == null) {
                        println("Cannot recover document URI from $filePath")
                        return
                    }
                } else {
                    // 3. Обычный файл из файловой системы
                    val file = File(filePath)
                    if (file.exists()) {
                        finalUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else {
                        println("File not found: $filePath")
                        return
                    }
                }
            }
        }
        else -> {
            println("Unsupported path format: $filePath")
            return
        }
    }

    if (finalUri == null) {
        println("Failed to create URI from: $filePath")
        return
    }

    val mimeType = if (finalUri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.getType(finalUri) ?: guessMimeTypeFromUri(finalUri)
    } else {
        guessMimeType(finalUri.toString())
    } ?: "*/*"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(finalUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        println("No app can handle this file type: $mimeType")
    }
}

// Функция для определения MIME-типа по расширению из URI
private fun guessMimeTypeFromUri(uri: Uri): String? {
    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}

private fun guessMimeType(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        else -> null
    }
}