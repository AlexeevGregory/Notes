package com.gregory.mynotes.data

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val timestamp: Long,
    val isFavorite: Boolean,
    val isDeleted: Boolean = false,
    val lastSyncTimestamp: Long,
)