package com.gregory.mynotes.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.CompletableDeferred
import java.awt.Desktop
import java.net.URI
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

actual object GoogleAuth {
    private const val CLIENT_ID = "your-client-id"
    private const val CLIENT_SECRET = "your-client-secret"
    private const val REDIRECT_URI = "http://localhost:8080/callback"
    private val apiKey = "your-api-key"


    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    actual suspend fun signIn(): GoogleUser {
        val authCode = CompletableDeferred<String>()
        val server = embeddedServer(Netty, port = 8080) {
            routing {
                get("/callback") {
                    val code = call.parameters["code"]
                    val error = call.parameters["error"]

                    if (error != null) {
                        authCode.completeExceptionally(Exception("Google auth error: $error"))
                        call.respondText("Ошибка авторизации: $error")
                    } else if (code != null) {
                        authCode.complete(code)
                        call.respondText("Авторизация успешна. Можете закрыть окно.")
                    } else {
                        authCode.completeExceptionally(Exception("No code in callback"))
                        call.respondText("Ошибка: код не получен")
                    }
                }
            }
        }
        server.start(wait = false)

        val scope = listOf(
            "email",
            "profile",
            "https://www.googleapis.com/auth/datastore"
        ).joinToString("%20")

        val authUrl =
            "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=$CLIENT_ID" +
                    "&redirect_uri=$REDIRECT_URI" +
                    "&response_type=code" +
                    "&scope=$scope"

        Desktop.getDesktop().browse(URI(authUrl))
        val code = authCode.await()
        server.stop()

        val response = client.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("code", code)
                append("client_id", CLIENT_ID)
                append("client_secret", CLIENT_SECRET)
                append("redirect_uri", REDIRECT_URI)
                append("grant_type", "authorization_code")
            }
        )
        val responseText = response.bodyAsText()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val tokenResponse = try {
            json.decodeFromString<TokenResponse>(responseText)
        } catch (e: SerializationException) {
            try {
                val errorResponse = json.decodeFromString<GoogleErrorResponse>(responseText)
                throw Exception("Google API error: ${errorResponse.error} - ${errorResponse.error_description}")
            } catch (e2: SerializationException) {
                throw Exception("Failed to parse Google response: $responseText", e)
            }
        }
        val firebaseResponse: FirebaseSignInResponse =
            client.post(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey"
            ) {
                contentType(ContentType.Application.Json)
                setBody(
                    FirebaseSignInRequest(
                        postBody =
                            "id_token=${tokenResponse.id_token}&providerId=google.com",
                        requestUri = "http://localhost",
                        returnSecureToken = true
                    )
                )
            }.body()

        // Получаем информацию о пользователе
        val userInfo: GoogleUserInfo = client.get("https://www.googleapis.com/oauth2/v2/userinfo") {
            bearerAuth(tokenResponse.access_token)
        }.body()


        return GoogleUser(
            id = userInfo.email.replace(".", "_").replace("@", "_"),
            email = userInfo.email,
            name = userInfo.name,
            picture = userInfo.picture,
            accessToken = firebaseResponse.idToken
        )
    }

    @Serializable
    data class TokenResponse(
        val access_token: String,
        val expires_in: Int,
        val token_type: String,
        val refresh_token: String? = null,
        val id_token: String? = null,
        val scope: String? = null
    )

    @Serializable
    data class FirebaseSignInRequest(
        val postBody: String,
        val requestUri: String,
        val returnSecureToken: Boolean
    )

    @Serializable
    data class FirebaseSignInResponse(
        val idToken: String,
        val refreshToken: String,
        val localId: String,
        val email: String? = null
    )

    @Serializable
    data class GoogleErrorResponse(
        val error: String,
        val error_description: String? = null,
        val error_uri: String? = null
    )

    @Serializable
    data class GoogleUserInfo(
        val id: String,
        val email: String,
        val name: String,
        val picture: String? = null
    )
}