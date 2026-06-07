package com.gregory.mynotes.platform

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FilePickerLauncher(
    onResult: (List<PickedFile>) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val files = uris.mapNotNull { uri ->
            val name = getFileNameFromUri(context, uri)
            val mimeType = context.contentResolver.getType(uri)
            if (name != null) PickedFile(uri.toString(), name, mimeType) else null
        }
        onResult(files)
    }

    LaunchedEffect(Unit) {
        launcher.launch("*/*")
    }
}

@SuppressLint("Range")
private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME))
            }
        }
    }
    if (name == null) {
        name = uri.lastPathSegment
    }
    return name
}