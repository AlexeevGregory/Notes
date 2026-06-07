package com.gregory.mynotes.data

enum class AttachmentType {
    IMAGE, VIDEO, FILE
}

data class Attachment(
    val id: Long,
    val noteId: Long,
    val filePath: String,
    val fileName: String,
    val mimeType: String?,
    val type: AttachmentType,
    val storagePath: String = ""
)

data class PendingAttachment(
    val filePath: String,
    val fileName: String,
    val mimeType: String?
)