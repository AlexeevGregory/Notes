package com.gregory.mynotes.platform

import androidx.compose.runtime.Composable

data class PickedFile(
    val uri: String,
    val name: String,
    val mimeType: String?
)

@Composable
expect fun FilePickerLauncher(
    onResult: (List<PickedFile>) -> Unit
)