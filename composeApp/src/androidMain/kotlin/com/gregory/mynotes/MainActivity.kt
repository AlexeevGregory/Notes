package com.gregory.mynotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import com.gregory.mynotes.auth.GoogleAuth
import com.gregory.mynotes.data.NoteRepository
import com.gregory.mynotes.data.NotebookRepository
import com.gregory.mynotes.db.DatabaseDriverFactory
import com.gregory.mynotes.platform.initPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.google.firebase.FirebaseApp
import com.gregory.mynotes.platform.initFileSystem

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initFileSystem(this)
        GoogleAuth.init(this)

        setContent {
            var noteRepository by remember { mutableStateOf<NoteRepository?>(null) }
            var notebookRepository by remember { mutableStateOf<NotebookRepository?>(null) }
            val scope = rememberCoroutineScope()

            FirebaseApp.initializeApp(this)

            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.IO) {
                    initPlatform(applicationContext)
                    val driverFactory = DatabaseDriverFactory(applicationContext)
                    val driver = driverFactory.createDriver()

                    noteRepository = NoteRepository(driver)
                    notebookRepository = NotebookRepository(driver)
                }
            }

            if (noteRepository != null && notebookRepository != null) {
                App(
                    noteRepository = noteRepository!!,
                    notebookRepository = notebookRepository!!,
                    coroutineScope = scope,
                    platformContext = this
                )
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        GoogleAuth.handleActivityResult(requestCode, data)
    }
}