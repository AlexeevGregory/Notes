package com.gregory.mynotes.data

import kotlinx.serialization.Serializable

@Serializable
data class FirestoreValue(
    val stringValue: String? = null,
    val integerValue: String? = null,
    val booleanValue: Boolean? = null
)

@Serializable
data class FirestoreFields(
    val id: FirestoreValue,
    val title: FirestoreValue,
    val content: FirestoreValue,
    val timestamp: FirestoreValue,
    val isFavorite: FirestoreValue,
    val isDeleted: FirestoreValue,
    val lastSyncTimestamp: FirestoreValue
)

@Serializable
data class FirestoreDocument(
    val fields: FirestoreFields
)

@Serializable
data class DocumentItem(
    val name: String,
    val fields: FirestoreFields
)

@Serializable
data class UserDocument(
    val fields: UserFields
)

@Serializable
data class UserFields(
    val id: FirestoreValue
)

//@Serializable
//data class GenericFirestoreDocument(
//    val name: String,
//    val fields: Map<String, FirestoreValue>
//)

// Заменим существующий RunQueryResponseElement на этот:
//@Serializable
//data class RunQueryResponseElement(
//    val document: GenericFirestoreDocument? = null,
//    val readTime: String? = null
//)

@Serializable
data class AttachmentMetadataDocument(
    val fields: Map<String, FirestoreValue>
)

@Serializable
data class AttachmentMetadata(
    val id: Long,
    val noteId: Long,
    val fileName: String,
    val mimeType: String?,
    val type: AttachmentType,
    val storagePath: String,
    val uploadedAt: Long = 0
)

@Serializable
data class RunQueryRequest(
    val parent: String? = null,
    val structuredQuery: StructuredQuery
)

@Serializable
data class StructuredQuery(val from: List<CollectionSelector>)

@Serializable
data class CollectionSelector(val collectionId: String, val allDescendants: Boolean = false)

@Serializable
data class RunQueryResponseElement(
    val document: FirestoreDocument? = null,
    val readTime: String? = null
)