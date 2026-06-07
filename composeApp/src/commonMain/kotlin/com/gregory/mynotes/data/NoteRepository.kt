package com.gregory.mynotes.data

import com.gregory.mynotes.db.NotesDatabase
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteRepository(
    private val driver: SqlDriver,
) {
    private val database = NotesDatabase(driver)
    private val queries = database.noteQueries

    // =========================
    // NOTES
    // =========================

    suspend fun getAllNotes(): List<Note> =
        withContext(Dispatchers.IO) {
            queries.selectAll()
                .executeAsList()
                .map { note ->
                    Note(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        timestamp = note.timestamp,
                        isFavorite = note.is_favorite == 1L,
                        isDeleted = note.is_deleted == 1L,
                        lastSyncTimestamp = note.last_sync_timestamp
                    )
                }
        }

    suspend fun getNoteById(id: Long): Note? {
        return queries.selectNoteById(id)
            .executeAsOneOrNull()
            ?.let { note ->
                Note(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    timestamp = note.timestamp,
                    isFavorite = note.is_favorite == 1L,
                    isDeleted = note.is_deleted == 1L,
                    lastSyncTimestamp = note.last_sync_timestamp
                )
            }
    }

    suspend fun insertNote(
        title: String,
        content: String,
        timestamp: Long
    ): Long {
        queries.insertNote(
            title = title,
            content = content,
            timestamp = timestamp,
            is_favorite = 0,
            is_deleted = 0,
            last_sync_timestamp = System.currentTimeMillis()
        )
        return queries.getLastInsertedId().executeAsOne()
    }

    suspend fun insertExistingNote(note: Note) {
        queries.insertExistingNote(
            id = note.id,
            title = note.title,
            content = note.content,
            timestamp = note.timestamp,
            is_favorite = if (note.isFavorite) 1 else 0,
            is_deleted = if (note.isDeleted) 1 else 0,
            last_sync_timestamp = note.lastSyncTimestamp
        )
    }

    suspend fun updateNote(note: Note) {
        queries.updateSyncedNote(
            title = note.title,
            content = note.content,
            timestamp = note.timestamp,
            is_favorite = if (note.isFavorite) 1 else 0,
            is_deleted = if (note.isDeleted) 1 else 0,
            last_sync_timestamp = note.lastSyncTimestamp,
            id = note.id
        )
    }

    suspend fun markNoteDeleted(id: Long) {
        queries.markNoteDeleted(
            deleted_at = System.currentTimeMillis(),
            last_sync_timestamp = System.currentTimeMillis(),
            id = id
        )
    }

    suspend fun restoreNote(noteId: Long) {
        queries.restoreNote(
            last_sync_timestamp = System.currentTimeMillis(),
            id = noteId
        )
    }

    suspend fun permanentlyDeleteExpiredNotes(expirationDays: Int = 3) {
        val threshold = System.currentTimeMillis() - expirationDays * 24L * 60 * 60 * 1000
        queries.permanentlyDeleteExpiredNotes(threshold)
    }

    suspend fun getExpiredDeletedNotes(expirationDays: Int = 3): List<Note> = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - expirationDays * 24L * 60 * 60 * 1000
        queries.selectExpiredDeletedNotes(threshold).executeAsList().map { row ->
            Note(
                id = row.id,
                title = row.title,
                content = row.content,
                timestamp = row.timestamp,
                isFavorite = row.is_favorite == 1L,
                isDeleted = row.is_deleted == 1L,
                lastSyncTimestamp = row.last_sync_timestamp
            )
        }
    }

    suspend fun clearAllNotes() {
        queries.deleteAllNotes()
    }

    suspend fun detachNotebookFromNotes(notebookId: Long) {
        queries.detachNotebookFromNotes(notebookId)
    }

    // =========================
    // ATTACHMENTS
    // =========================

    suspend fun getAttachmentsForNote(noteId: Long): List<Attachment> {

        return queries.selectAttachmentsForNote(noteId)
            .executeAsList()
            .map { row ->

                Attachment(
                    id = row.id,
                    noteId = row.note_id,
                    filePath = row.file_path,
                    fileName = row.file_name,
                    mimeType = row.mime_type,
                    type = AttachmentType.entries[row.attachment_type.toInt()]
                )
            }
    }

    suspend fun addAttachment(attachment: Attachment): Long {
        queries.insertAttachment(
            attachment.noteId,
            attachment.filePath,
            attachment.fileName,
            attachment.mimeType,
            attachment.type.ordinal.toLong()
        )
        return queries.getLastInsertedIdAttachment().executeAsOne()
    }

    suspend fun deleteAttachment(attachmentId: Long) {

        queries.deleteAttachmentById(attachmentId)
    }

    suspend fun deleteAllAttachmentsForNote(noteId: Long) {

        queries.deleteAttachmentsForNote(noteId)
    }

    suspend fun addOrUpdateAttachment(attachment: Attachment) {
        if (getAttachmentById(attachment.id) != null) {
            queries.updateAttachment(
                file_path = attachment.filePath,
                file_name = attachment.fileName,
                mime_type = attachment.mimeType,
                attachment_type = attachment.type.ordinal.toLong(),
                id = attachment.id
            )
        } else {
            queries.insertAttachment(
                note_id = attachment.noteId,
                file_path = attachment.filePath,
                file_name = attachment.fileName,
                mime_type = attachment.mimeType,
                attachment_type = attachment.type.ordinal.toLong()
            )
        }
    }

    suspend fun getAttachmentById(id: Long): Attachment? {
        return queries.selectAttachmentById(id).executeAsOneOrNull()?.let { row ->
            Attachment(
                id = row.id,
                noteId = row.note_id,
                filePath = row.file_path,
                fileName = row.file_name,
                mimeType = row.mime_type,
                type = AttachmentType.entries[row.attachment_type.toInt()]
            )
        }
    }
}