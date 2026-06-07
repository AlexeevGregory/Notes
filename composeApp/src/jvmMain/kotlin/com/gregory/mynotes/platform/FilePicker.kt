package com.gregory.mynotes.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.darkrockstudios.libraries.mpfilepicker.MultipleFilePicker

@Composable
actual fun FilePickerLauncher(
    onResult: (List<PickedFile>) -> Unit
) {
    val showPicker = remember { mutableStateOf(true) }

    if (showPicker.value) {
        MultipleFilePicker(
            show = true,
            fileExtensions = listOf(
                "jpg", "jpeg", "png", "gif",
                "mp4", "avi", "mov",
                "pdf", "txt", "doc", "docx", "xls", "xlsx"
            ),
            title = "Выберите файлы"
        ) { files ->
            val result = files?.mapNotNull { mpFile ->
                val path = mpFile.path
                if (path.isNotBlank()) {
                    val name = path.substringAfterLast('/').substringAfterLast('\\')
                    val mimeType = guessMimeType(name)
                    PickedFile(path, name, mimeType)
                } else null
            } ?: emptyList()
            onResult(result)
            showPicker.value = false
        }
    }
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