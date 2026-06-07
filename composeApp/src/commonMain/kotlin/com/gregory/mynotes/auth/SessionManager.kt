package com.gregory.mynotes.auth

import com.russhwolf.settings.Settings

class SessionManager(
    private val settings: Settings
) {
    companion object {
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_TOKEN = "user_token"
    }

    fun saveUser(user: GoogleUser) {
        settings.putString(KEY_ID, user.id)
        settings.putString(KEY_NAME, user.name)
        settings.putString(KEY_EMAIL, user.email)
        settings.putString(KEY_TOKEN, user.accessToken)
    }

    fun getUser(): GoogleUser? {
        val id = settings.getStringOrNull(KEY_ID) ?: return null

        return GoogleUser(
            id = id,
            name = settings.getString(KEY_NAME, ""),
            email = settings.getString(KEY_EMAIL, ""),
            picture = null,
            accessToken = settings.getString(KEY_TOKEN, "")
        )
    }

    fun clearSession() {
        settings.remove(KEY_ID)
        settings.remove(KEY_NAME)
        settings.remove(KEY_EMAIL)
    }
}