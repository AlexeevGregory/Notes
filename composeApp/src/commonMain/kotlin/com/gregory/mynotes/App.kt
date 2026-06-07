package com.gregory.mynotes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gregory.mynotes.presentation.NotesViewModel
import com.gregory.mynotes.data.Note
import com.gregory.mynotes.auth.GoogleAuth
import com.gregory.mynotes.auth.GoogleUser
import com.gregory.mynotes.auth.SessionManager
import com.gregory.mynotes.platform.createSettings
import com.gregory.mynotes.sync.FirebaseSyncManager
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material.icons.filled.Restore
import com.gregory.mynotes.data.NoteRepository
import com.gregory.mynotes.data.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val date = dateTime.date
    return "${date.day}.${date.monthNumber}.${date.year} ${String.format("%02d:%02d", dateTime.hour, dateTime.minute)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    noteRepository: NoteRepository,
    notebookRepository: NotebookRepository,
    coroutineScope: CoroutineScope,
    platformContext: Any? = null
) {
    val notesViewModel = remember { NotesViewModel(noteRepository, notebookRepository, coroutineScope) }
    val sessionManager = remember { SessionManager(createSettings()) }
    var currentUser by remember { mutableStateOf(sessionManager.getUser()) }
    var isLoading by remember { mutableStateOf(false) }
    var syncManager by remember { mutableStateOf<FirebaseSyncManager?>(null) }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            val newSyncManager = FirebaseSyncManager(
                noteRepository,
                user.id,
                user.accessToken,
                platformContext
            )
            syncManager = newSyncManager
            notesViewModel.setSyncManager(newSyncManager)
            newSyncManager.syncFromCloud()
            notesViewModel.loadNotes()
            newSyncManager.autoDelete()
        } ?: run {
            notesViewModel.clearAllNotes()
            syncManager = null
        }
    }

    MaterialTheme {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            currentUser == null -> {
                LoginScreen(
                    onLogin = {
                        coroutineScope.launch {
                            try {
                                isLoading = true
                                val user = GoogleAuth.signIn()
                                sessionManager.saveUser(user)
                                currentUser = user
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }
            else -> {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "list"
                ) {
                    composable("list") {
                        NotesListScreen(
                            viewModel = notesViewModel,
                            onAddNote = {
                                notesViewModel.cancelEditing()
                                navController.navigate("edit")
                            },
                            onEditNote = { note ->
                                notesViewModel.startEditing(note)
                                navController.navigate("edit")
                            },
                            currentUser = currentUser,
                            onLogout = {
                                sessionManager.clearSession()
                                currentUser = null
                            }
                        )
                    }
                    composable("edit") {
                        NoteEditScreen(
                            viewModel = notesViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onLogin) {
            Text("Войти через Google")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onAddNote: () -> Unit,
    onEditNote: (Note) -> Unit,
    currentUser: GoogleUser?,
    onLogout: () -> Unit,
) {
    LaunchedEffect(viewModel.showDeleted) {
        viewModel.loadNotes()
        viewModel.autoDelete()
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf("notes") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text =
                        if (currentUser == null)
                            "Гость"
                        else
                            currentUser!!.email,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Выйти (${currentUser?.name ?: ""})") },
                    selected = false,
                    onClick = {
                        onLogout()
                        scope.launch { drawerState.close() }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = null
                        )
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Заметки") },
                    selected = selectedItem == "notes",
                    onClick = {
                        selectedItem = "notes"
                        viewModel.toggleDeletedFilter(false)
                        scope.launch { drawerState.close() }
                    },
                    icon = {
                        Icon(Icons.Default.Note, contentDescription = null)
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Корзина") },
                    selected = selectedItem == "trash",
                    onClick = {
                        selectedItem = "trash"
                        viewModel.toggleDeletedFilter(true)
                        scope.launch { drawerState.close() }
                    },
                    icon = {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (viewModel.showDeleted) "Корзина" else "Мои заметки") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Меню"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onAddNote) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Добавить"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.notes.isNotEmpty()) {
                    items(viewModel.notes) { note ->
                        NoteCard(
                            note = note,
                            onEdit = {
                                if (!viewModel.showDeleted)
                                    onEditNote(note)
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(
                                    note.id,
                                    note.isFavorite
                                )
                            },
                            onRestore = { viewModel.restoreNote(note) },

                            showDeleted = viewModel.showDeleted
                        )
                    }
                } else {
                    item {
                        Text("На данный момент нет заметок...")
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRestore: () -> Unit,
    showDeleted: Boolean
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onEdit() }
    ) {

        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    formatTimestamp(note.timestamp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showDeleted) {

                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {

                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "Восстановить"
                    )
                }

            } else {

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {

                    Icon(
                        imageVector =
                            if (note.isFavorite)
                                Icons.Default.Favorite
                            else
                                Icons.Default.FavoriteBorder,

                        contentDescription =
                            if (note.isFavorite)
                                "Убрать из избранного"
                            else
                                "В избранное"
                    )
                }
            }
        }
    }
}