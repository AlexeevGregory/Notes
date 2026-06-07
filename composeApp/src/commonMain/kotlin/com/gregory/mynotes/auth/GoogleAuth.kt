package com.gregory.mynotes.auth

expect object GoogleAuth {
    suspend fun signIn(): GoogleUser
}

data class GoogleUser(
    val id: String,
    val email: String,
    val name: String,
    val picture: String?,
    val accessToken: String
)