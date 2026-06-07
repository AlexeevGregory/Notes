package com.gregory.mynotes

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gregory.mynotes.data.NoteRepository
import com.gregory.mynotes.data.NotebookRepository
import com.gregory.mynotes.db.DatabaseDriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    application {

        val driverFactory = DatabaseDriverFactory()
        val noteRepository = NoteRepository(driver = driverFactory.createDriver())
        val noteBookRepository = NotebookRepository(driver = driverFactory.createDriver())

        Window(
            onCloseRequest = ::exitApplication,
            title = "Мои заметки"
        ) {

            App(
                noteRepository = noteRepository,
                notebookRepository = noteBookRepository,
                coroutineScope = CoroutineScope(Dispatchers.Main),
                platformContext = null
            )
        }
    }
}