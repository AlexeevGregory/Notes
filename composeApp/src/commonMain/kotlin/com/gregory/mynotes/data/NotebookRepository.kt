package com.gregory.mynotes.data

import app.cash.sqldelight.db.SqlDriver
import com.gregory.mynotes.db.NotesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotebookRepository(private val driver: SqlDriver) {
    private val database = NotesDatabase(driver)
    private val queries = database.noteQueries

    suspend fun getAllNotebooks(includeDeleted: Boolean = false): List<Notebook> = withContext(Dispatchers.IO) {
        val all = queries.selectAllNotebooks().executeAsList()
        all.filter { row ->
            if (includeDeleted) {
                true
            } else {
                row.is_deleted == 0L
            }
        }.map { row ->
            Notebook(
                id = row.id,
                name = row.name,
                color = row.color ?: "#FFFFFF",
                isDeleted = row.is_deleted == 1L,
                lastSyncTimestamp = row.last_sync_timestamp,
                deletedAt = row.deleted_at
            )
        }
    }

    suspend fun getNotebookById(id: Long): Notebook? = withContext(Dispatchers.IO) {
        queries.selectNotebookById(id).executeAsOneOrNull()?.let { row ->
            Notebook(
                id = row.id,
                name = row.name,
                color = row.color ?: "#FFFFFF",
                isDeleted = row.is_deleted == 1L,
                lastSyncTimestamp = row.last_sync_timestamp,
                deletedAt = row.deleted_at
            )
        }
    }

    suspend fun insertNotebook(name: String, color: String): Long = withContext(Dispatchers.IO) {
        queries.insertNotebook(
            name = name,
            color = color,
            last_sync_timestamp = System.currentTimeMillis()
        )
        queries.getLastInsertedId().executeAsOne()
    }

    suspend fun updateNotebook(notebook: Notebook) {
        queries.updateNotebook(
            name = notebook.name,
            color = notebook.color,
            is_deleted = if (notebook.isDeleted) 1L else 0L,
            last_sync_timestamp = notebook.lastSyncTimestamp,
            id = notebook.id
        )
    }

    suspend fun markNotebookDeleted(id: Long) {
        queries.markNotebookDeleted(
            deleted_at = System.currentTimeMillis(),
            last_sync_timestamp = System.currentTimeMillis(),
            id = id
        )
    }

    suspend fun restoreNotebook(id: Long) {
        queries.restoreNotebook(
            last_sync_timestamp = System.currentTimeMillis(),
            id = id
        )
    }

    suspend fun getExpiredDeletedNotebooks(expirationDays: Int = 30): List<Notebook> = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() - expirationDays * 24L * 60 * 60 * 1000
        queries.selectExpiredDeletedNotebooks(threshold).executeAsList().map { row ->
            Notebook(
                id = row.id,
                name = row.name,
                color = row.color ?: "#FFFFFF",
                isDeleted = row.is_deleted == 1L,
                lastSyncTimestamp = row.last_sync_timestamp,
                deletedAt = row.deleted_at
            )
        }
    }

    suspend fun permanentlyDeleteExpiredNotebooks(noteRepository: NoteRepository, expirationDays: Int = 30) {
        val expired = getExpiredDeletedNotebooks(expirationDays)
        expired.forEach { notebook ->
            noteRepository.detachNotebookFromNotes(notebook.id)
            queries.permanentlyDeleteNotebook(notebook.id)
        }
    }
}