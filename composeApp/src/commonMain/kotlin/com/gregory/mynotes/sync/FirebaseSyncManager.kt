package com.gregory.mynotes.sync

import android.content.Context
import com.gregory.mynotes.data.NoteRepository

expect class FirebaseSyncManager(
    repository: NoteRepository,
    userId: String,
    authToken: String,
    platformContext: Any? = null
) : SyncManager
























