package com.example.finalapp.data.repository

import com.example.finalapp.data.model.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SessionStore {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    fun setSession(token: String?, user: UserDto?) {
        _token.value = token
        _user.value = user
    }

    fun updateUser(user: UserDto?) {
        _user.value = user
    }

    fun updateUser(transform: (UserDto?) -> UserDto?) {
        _user.update(transform)
    }

    fun clear() {
        _token.value = null
        _user.value = null
    }
}
