package com.gregory.mynotes.sync

import com.gregory.mynotes.data.Attachment
import com.gregory.mynotes.data.Note
import java.io.File

interface SyncManager {
    suspend fun uploadNote(note: Note)
    suspend fun deleteNotePermanently(noteId: Long)
    suspend fun syncFromCloud()
    suspend fun autoDelete(expirationDays: Int = 30)

    suspend fun uploadAttachment(noteId: Long, attachment: Attachment, localFile: File)
    suspend fun downloadAttachment(noteId: Long, fileName: String, localFile: File)
    suspend fun deleteAttachment(noteId: Long, attachmentId: Long)
    suspend fun syncAttachmentsForNote(noteId: Long)

}