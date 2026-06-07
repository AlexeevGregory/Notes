package com.gregory.mynotes.presentation

import androidx.compose.runtime.*
import com.gregory.mynotes.data.*
import com.gregory.mynotes.sync.FirebaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotesViewModel(
    private val repository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val coroutineScope: CoroutineScope,
) {
    val notes = mutableStateListOf<Note>()
    private var syncManager: FirebaseSyncManager? = null
    var titleInput by mutableStateOf("")
        private set
    var contentInput by mutableStateOf("")
        private set

    fun setSyncManager(manager: FirebaseSyncManager) {
        this.syncManager = manager
    }
    fun updateTitleInput(newTitle: String) { titleInput = newTitle }
    fun updateContentInput(newContent: String) { contentInput = newContent }

    var editingNoteId by mutableStateOf<Long?>(null)
        private set
    var showDeleted by mutableStateOf(false)
        private set

    fun toggleDeletedFilter(value: Boolean) {
        showDeleted = value
        coroutineScope.launch(Dispatchers.IO) { refreshNotes() }
    }

    val isEditing: Boolean get() = editingNoteId != null
    val attachments = mutableStateListOf<Attachment>()
    private val pendingAttachments = mutableStateListOf<PendingAttachment>()
    val allAttachments: List<Any> get() = attachments + pendingAttachments

    fun startEditing(note: Note) {
        titleInput = note.title
        contentInput = note.content
        editingNoteId = note.id
        loadAttachmentsForNote(note.id)
        pendingAttachments.clear()
    }


    fun cancelEditing() {
        titleInput = ""
        contentInput = ""
        editingNoteId = null
        attachments.clear()
        pendingAttachments.clear()
    }

    fun saveNote() {
        if (titleInput.isBlank() && contentInput.isBlank()) return

        val finalTitle = titleInput.ifBlank { "Без заголовка" }
        val finalContent = contentInput.ifBlank { "Нет содержимого" }
        val timestamp = System.currentTimeMillis()

        coroutineScope.launch(Dispatchers.IO) {
            if (editingNoteId != null) {
                val oldNote = repository.getNoteById(editingNoteId!!)
                oldNote?.let {
                    val updated =
                        it.copy(
                            title = finalTitle,
                            content = finalContent,
                            timestamp = timestamp,
                            lastSyncTimestamp =
                                System.currentTimeMillis()
                        )
                    repository.updateNote(updated)
                    syncManager?.uploadNote(updated)
                }
            } else {
                val newId =
                    repository.insertNote(
                        title = finalTitle,
                        content = finalContent,
                        timestamp = timestamp
                    )
                val created = repository.getNoteById(newId)
                created?.let { syncManager?.uploadNote(it) }

            }
            refreshNotes()
            withContext(Dispatchers.Main) {
                cancelEditing()
            }
        }
    }

    private suspend fun refreshNotes() {

        val all = repository.getAllNotes()

        val loadedNotes =
            all.filter { it.isDeleted == showDeleted }

        withContext(Dispatchers.Main) {

            notes.clear()
            notes.addAll(loadedNotes)
        }
    }

    fun loadNotes() {
        coroutineScope.launch(Dispatchers.IO) {
            refreshNotes()
        }
    }

    fun deleteCurrentNote() {
        val noteId = editingNoteId ?: return
        coroutineScope.launch(Dispatchers.IO) {
            repository.markNoteDeleted(noteId)
            val deletedNote = repository.getNoteById(noteId)
            deletedNote?.let { syncManager?.uploadNote(it) }
            refreshNotes()
            withContext(Dispatchers.Main) { cancelEditing() }
        }
    }

    suspend fun clearAllNotes() {
        repository.clearAllNotes()
        withContext(Dispatchers.Main) {
            notes.clear()
        }
    }

    fun autoDelete(expirationDays: Int = 3) {
        coroutineScope.launch(Dispatchers.IO) {
            syncManager?.autoDelete(expirationDays)
            refreshNotes()
        }
    }

    fun toggleFavorite(
        noteId: Long,
        currentIsFavorite: Boolean
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            val note = repository.getNoteById(noteId) ?: return@launch

            val updated =
                note.copy(
                    isFavorite = !currentIsFavorite,
                    lastSyncTimestamp =
                        System.currentTimeMillis()
                )

            repository.updateNote(updated)
            syncManager?.uploadNote(updated)
            refreshNotes()
        }
    }

    suspend fun removeNote(note: Note) {

        val deleted =
            note.copy(
                isDeleted = true,
                lastSyncTimestamp =
                    System.currentTimeMillis()
            )

        repository.updateNote(deleted)
    }

    fun restoreNote(note: Note) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.restoreNote(note.id)   // сбрасывает is_deleted и deleted_at
            val restored = repository.getNoteById(note.id)
            restored?.let { syncManager?.uploadNote(it) }
            refreshNotes()
        }
    }

    private fun loadAttachmentsForNote(noteId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            val loaded = repository.getAttachmentsForNote(noteId)
            withContext(Dispatchers.Default) {
                attachments.clear()
                attachments.addAll(loaded)
            }
        }
    }

    fun addAttachment(filePath: String, fileName: String, mimeType: String?) {
        val noteId = editingNoteId
        if (noteId != null) {
            val type = when {
                mimeType?.startsWith("image/") == true -> AttachmentType.IMAGE
                mimeType?.startsWith("video/") == true -> AttachmentType.VIDEO
                else -> AttachmentType.FILE
            }
            val attachment = Attachment(
                id = 0L,
                noteId = noteId,
                filePath = filePath,
                fileName = fileName,
                mimeType = mimeType,
                type = type,
                storagePath = ""
            )
            coroutineScope.launch(Dispatchers.IO) {
                val insertedId = repository.addAttachment(attachment)
                val fullAttachment = repository.getAttachmentById(insertedId)
                fullAttachment?.let {
                    val file = File(it.filePath)
                    if (file.exists()) {
                        syncManager?.uploadAttachment(noteId, it, file)
                    }
                }
                loadAttachmentsForNote(noteId)
            }
        } else {
            pendingAttachments.add(PendingAttachment(filePath, fileName, mimeType))
        }
    }


    fun removeAttachment(attachment: Any) {
        when (attachment) {
            is Attachment -> {
                coroutineScope.launch(Dispatchers.IO) {
                    syncManager?.deleteAttachment(attachment.noteId, attachment.id)
                    repository.deleteAttachment(attachment.id)
                    loadAttachmentsForNote(attachment.noteId)
                }
            }
            is PendingAttachment -> {
                pendingAttachments.remove(attachment)
            }
        }
    }
}