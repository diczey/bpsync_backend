package com.example.finalapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.finalapp.data.model.UserDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SessionStore {
    private const val PREFS_NAME = "bpsync_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_JSON = "user_json"

    private val gson = Gson()

    @Volatile
    private var preferences: SharedPreferences? = null

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return

        synchronized(this) {
            if (preferences != null) return

            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences = prefs
            _token.value = prefs.getString(KEY_TOKEN, null)
            _user.value = prefs.getString(KEY_USER_JSON, null)?.let { userJson ->
                runCatching { gson.fromJson(userJson, UserDto::class.java) }.getOrNull()
            }
        }
    }

    fun setSession(token: String?, user: UserDto?) {
        _token.value = token
        _user.value = user
        persist()
    }

    fun updateUser(user: UserDto?) {
        _user.value = user
        persist()
    }

    fun updateUser(transform: (UserDto?) -> UserDto?) {
        _user.update(transform)
        persist()
    }

    fun clear() {
        _token.value = null
        _user.value = null
        persist()
    }

    fun hasActiveSession(): Boolean {
        return !_token.value.isNullOrBlank()
    }

    private fun persist() {
        val prefs = preferences ?: return
        val editor = prefs.edit()
        val currentToken = _token.value
        val currentUser = _user.value

        if (currentToken.isNullOrBlank()) {
            editor.remove(KEY_TOKEN)
        } else {
            editor.putString(KEY_TOKEN, currentToken)
        }

        if (currentUser == null) {
            editor.remove(KEY_USER_JSON)
        } else {
            editor.putString(KEY_USER_JSON, gson.toJson(currentUser))
        }

        editor.apply()
    }
}
