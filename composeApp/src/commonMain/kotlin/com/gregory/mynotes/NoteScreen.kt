package com.gregory.mynotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.gregory.mynotes.presentation.NotesViewModel
import com.gregory.mynotes.data.Attachment
import com.gregory.mynotes.data.AttachmentType
import com.gregory.mynotes.data.PendingAttachment
import com.gregory.mynotes.platform.openFile
import com.gregory.mynotes.platform.FilePickerLauncher


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        FilePickerLauncher { pickedFiles ->
            pickedFiles.forEach { picked ->
                viewModel.addAttachment(
                    filePath = picked.uri,
                    fileName = picked.name,
                    mimeType = picked.mimeType
                )
            }
            showPicker = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Редактирование" else "Новая заметка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveNote(); onBack() }) {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить")
                    }
                    if (viewModel.isEditing) {
                        IconButton(onClick = {
                            viewModel.deleteCurrentNote()
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPicker = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить вложение")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.titleInput,
                onValueChange = viewModel::updateTitleInput,
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = viewModel.contentInput,
                onValueChange = viewModel::updateContentInput,
                label = { Text("Содержание") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(lineHeight = 20.sp)
            )

            Text("Вложения", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            if (viewModel.allAttachments.isEmpty()) {
                Text(
                    "Нет вложений. Нажмите на кнопку +, чтобы добавить файл, изображение или видео.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    items(viewModel.allAttachments.size) { index ->
                        val attachment = viewModel.allAttachments[index]
                        when (attachment) {
                            is Attachment -> {
                                AttachmentItem(
                                    attachment = attachment,
                                    onRemove = { viewModel.removeAttachment(attachment) },
                                    onOpen = { openFile(attachment.filePath) }
                                )
                            }
                            is PendingAttachment -> {
                                PendingAttachmentItem(
                                    attachment = attachment,
                                    onRemove = { viewModel.removeAttachment(attachment) },
                                    onOpen = { openFile(attachment.filePath) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentItem(attachment: Attachment, onRemove: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .size(150.dp)
            .clickable { onOpen() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (attachment.type) {
                    AttachmentType.IMAGE -> Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(40.dp))
                    AttachmentType.VIDEO -> Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(40.dp))
                    AttachmentType.FILE -> Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.fileName.take(12),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PendingAttachmentItem(attachment: PendingAttachment, onRemove: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable { onOpen() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.fileName.take(12),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
            }
        }
    }
}