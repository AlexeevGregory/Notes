package com.gregory.mynotes.sync

import com.gregory.mynotes.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

actual class FirebaseSyncManager actual constructor(
    private val repository: NoteRepository,
    val userId: String,
    private val authToken: String,
    private val platformContext: Any?
) : SyncManager {

    private val projectId = "your-project-id"
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/default/documents"
    private val bucket = "your-bucket"
    private val storageBaseUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o"

    private fun buildStoragePath(noteId: Long, fileName: String): String {
        return "users/$userId/notes/$noteId/attachments/$fileName"
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun createUserIfNeeded() {
        val url = "$baseUrl/users/$userId"
        val document = UserDocument(
            fields = UserFields(id = FirestoreValue(stringValue = userId))
        )
        try {
            client.patch(url) {
                contentType(ContentType.Application.Json)
                setBody(document)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ===================== ВЛОЖЕНИЯ =====================

    override suspend fun uploadAttachment(noteId: Long, attachment: Attachment, localFile: File) {
        createUserIfNeeded()
        val storagePath = "users/$userId/notes/$noteId/attachments/${attachment.fileName}"
        val encodedPath = storagePath.encodeURLPathPart()
        val uploadUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o?name=$encodedPath"

        val fileBytes = withContext(Dispatchers.IO) { localFile.readBytes() }

        val uploadResponse: HttpResponse = client.post(uploadUrl) {
            contentType(ContentType.Application.OctetStream)
            setBody(fileBytes)
        }
        if (!uploadResponse.status.isSuccess()) {
            throw Exception("Upload failed: ${uploadResponse.status}")
        }

        val docUrl = "$baseUrl/users/$userId/notes/$noteId/attachments/${attachment.fileName}"
        val document = mapOf(
            "fields" to mapOf(
                "fileName" to mapOf("stringValue" to attachment.fileName),
                "mimeType" to mapOf("stringValue" to (attachment.mimeType ?: "")),
                "type" to mapOf("stringValue" to attachment.type.name),
                "storagePath" to mapOf("stringValue" to storagePath),
                "noteId" to mapOf("integerValue" to noteId.toString())
            )
        )
        val docResponse: HttpResponse = client.patch(docUrl) {
            contentType(ContentType.Application.Json)
            setBody(document)
        }
        if (!docResponse.status.isSuccess()) {
            val errorBody = docResponse.bodyAsText()
            throw Exception("Failed to save attachment metadata: ${docResponse.status} - $errorBody")
        }
    }

    override suspend fun downloadAttachment(noteId: Long, fileName: String, localFile: File) {
        val storagePath = buildStoragePath(noteId, fileName)

        val encodedPath = storagePath.encodeURLPathPart()

        val url = "$storageBaseUrl/$encodedPath?alt=media"

        val response: HttpResponse = client.get(url)

        if (response.status == HttpStatusCode.OK) {
            val bytes = response.body<ByteArray>()

            withContext(Dispatchers.IO) {
                localFile.parentFile?.mkdirs()
                localFile.writeBytes(bytes)
            }
        } else {
            throw Exception("Failed to download attachment: ${response.status}")
        }
    }

    override suspend fun deleteAttachment(noteId: Long, attachmentId: Long) {
        val attachment = repository.getAttachmentById(attachmentId)
        val docUrl = "$baseUrl/users/$userId/notes/$noteId/attachments/${attachment?.fileName}"
        try {
            client.delete(docUrl)
            println("Deleted attachment metadata: $docUrl")
        } catch (e: Exception) {
            println("Failed to delete metadata: ${e.message}")
        }

        val objectPath = buildStoragePath(noteId, attachment!!.fileName)
        val encodedPath = objectPath.encodeURLPathPart()

        val deleteUrl = "$storageBaseUrl/$encodedPath"

        try {
            val response = client.delete(deleteUrl)

            if (response.status.isSuccess()) {
                println("Deleted storage object: $deleteUrl")
            } else {
                println("Failed to delete storage: ${response.status}")
                println(response.bodyAsText())
            }
        } catch (e: Exception) {
            println("Error deleting storage file: ${e.message}")
        }
    }

    override suspend fun syncAttachmentsForNote(noteId: Long) {
        val attachmentsUrl = "$baseUrl/users/$userId/notes/$noteId"
        val queryBody = RunQueryRequest(
            structuredQuery = StructuredQuery(
                from = listOf(CollectionSelector(collectionId = "attachments"))
            )
        )

        val response: HttpResponse = client.post("$attachmentsUrl:runQuery") {
            contentType(ContentType.Application.Json)
            setBody(queryBody)
        }

        if (response.status != HttpStatusCode.OK) {
            println("Failed to fetch attachments: ${response.status}")
            return
        }

        val jsonText = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val jsonArray = json.parseToJsonElement(jsonText).jsonArray
        val remoteAttachments = mutableListOf<AttachmentMetadata>()

        for (element in jsonArray) {
            val doc = element.jsonObject["document"]?.jsonObject ?: continue
            val name = doc["name"]?.jsonPrimitive?.content ?: continue
            val fields = doc["fields"]?.jsonObject ?: continue

            val fileName = fields["fileName"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: continue
            val mimeType = fields["mimeType"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content
            val typeStr = fields["type"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: "FILE"
            val storagePath = fields["storagePath"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: continue
            val noteIdField = fields["noteId"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content

            remoteAttachments.add(
                AttachmentMetadata(
                    id = 0L,
                    fileName = fileName,
                    mimeType = mimeType,
                    type = AttachmentType.valueOf(typeStr),
                    storagePath = storagePath,
                    noteId = noteIdField?.toLong() ?: noteId
                )
            )
        }

        val localAttachments = repository.getAttachmentsForNote(noteId)
        val localMap = localAttachments.associateBy { it.id }
        val remoteMap = remoteAttachments.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).toSet()

        for (id in allIds) {
            val local = localMap[id]
            val remote = remoteMap[id]

            when {
                local != null && remote == null -> {
                    val file = File(local.filePath)
                    if (file.exists()) {
                        uploadAttachment(noteId, local, file)
                    }
                }
                local == null && remote != null -> {
                    try {
                        val localFile = getLocalAttachmentFile(noteId, remote.fileName)
                        downloadAttachment(remote.noteId, remote.fileName, localFile)
                        repository.addAttachment(
                            Attachment(
                                id = remote.id,
                                noteId = noteId,
                                filePath = localFile.absolutePath,
                                fileName = remote.fileName,
                                mimeType = remote.mimeType,
                                type = remote.type
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getLocalAttachmentFile(noteId: Long, fileName: String): File {
        val dir = File(getAppDataDirectory(), "attachments/$userId/$noteId")
        dir.mkdirs()
        return File(dir, fileName)
    }

    private fun getAppDataDirectory(): File = File(System.getProperty("java.io.tmpdir"), "mynotes")

    // ===================== ЗАМЕТКИ =====================

    override suspend fun uploadNote(note: Note) {
        createUserIfNeeded()
        val url = "$baseUrl/users/$userId/notes/${note.id}"
        val document = FirestoreDocument(
            fields = FirestoreFields(
                id = FirestoreValue(integerValue = note.id.toString()),
                title = FirestoreValue(stringValue = note.title),
                content = FirestoreValue(stringValue = note.content),
                timestamp = FirestoreValue(integerValue = note.timestamp.toString()),
                isFavorite = FirestoreValue(booleanValue = note.isFavorite),
                isDeleted = FirestoreValue(booleanValue = note.isDeleted),
                lastSyncTimestamp = FirestoreValue(integerValue = note.lastSyncTimestamp.toString())
            )
        )
        client.patch(url) {
            contentType(ContentType.Application.Json)
            setBody(document)
        }

        val attachments = repository.getAttachmentsForNote(note.id)
        for (attachment in attachments) {
            val file = File(attachment.filePath)
            if (file.exists()) {
                uploadAttachment(note.id, attachment, file)
            }
        }
    }

    override suspend fun deleteNotePermanently(noteId: Long) {
        val url = "$baseUrl/users/$userId/notes/$noteId"
        try {
            client.delete(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun syncFromCloud() {
        val url = "$baseUrl/users/$userId:runQuery"

        val queryBody = RunQueryRequest(
            structuredQuery = StructuredQuery(
                from = listOf(CollectionSelector(collectionId = "notes"))
            )
        )

        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(queryBody)
        }

        if (response.status != HttpStatusCode.OK) {
            println("Cloud trouble: ${response.status}")
            return
        }

        val queryResults: List<RunQueryResponseElement> = response.body()

        val remoteNotes = queryResults.mapNotNull { it.document }.map { doc ->
            val f = doc.fields
            Note(
                id = f.id.integerValue!!.toLong(),
                title = f.title.stringValue!!,
                content = f.content.stringValue!!,
                timestamp = f.timestamp.integerValue!!.toLong(),
                isFavorite = f.isFavorite.booleanValue!!,
                isDeleted = f.isDeleted.booleanValue!!,
                lastSyncTimestamp = f.lastSyncTimestamp.integerValue!!.toLong()
            )
        }

        val localNotes = repository.getAllNotes()
        val localMap = localNotes.associateBy { it.id }
        val remoteMap = remoteNotes.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).toSet()

        for (id in allIds) {
            val local = localMap[id]
            val remote = remoteMap[id]

            when {
                local == null && remote != null -> {
                    if (!remote.isDeleted) {
                        repository.insertExistingNote(remote)
                        syncAttachmentsForNote(remote.id)
                    }
                }

                local != null && remote == null -> {
                    uploadNote(local)
                }

                local != null && remote != null -> {
                    if (remote.lastSyncTimestamp >= local.lastSyncTimestamp) {
                        if (remote.isDeleted) {
                            repository.markNoteDeleted(remote.id)
                        } else {
                            repository.updateNote(remote)
                            syncAttachmentsForNote(remote.id)
                        }
                    } else {
                        uploadNote(local)
                    }
                }
            }
        }
    }

    override suspend fun autoDelete(expirationDays: Int) {
        val expiredNotes = repository.getExpiredDeletedNotes(expirationDays)
        expiredNotes.forEach { note ->
            deleteNotePermanently(note.id)
        }
        repository.permanentlyDeleteExpiredNotes(expirationDays)
    }
}