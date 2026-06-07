package com.gregory.mynotes.data

data class Notebook(
    val id: Long,
    val name: String,
    val color: String = "#FFFFFF",
    val isDeleted: Boolean = false,
    val lastSyncTimestamp: Long,
    val deletedAt: Long? = null
)