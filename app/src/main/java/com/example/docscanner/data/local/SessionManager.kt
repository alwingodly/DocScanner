package com.example.docscanner.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("docscanner_session", Context.MODE_PRIVATE)

    fun saveSession(username: String) {
        prefs.edit { putString("username", username) }
    }

    fun getUsername(): String? = prefs.getString("username", null)

    fun isLoggedIn(): Boolean = getUsername() != null

    fun clearSession() {
        prefs.edit { clear() }
    }
}