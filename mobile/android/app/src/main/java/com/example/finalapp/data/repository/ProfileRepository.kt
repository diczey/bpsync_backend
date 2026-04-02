package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.UserDto

class ProfileRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchProfile(): RepositoryResult<UserDto> {
        val token = SessionStore.token.value

        if (token == null) {
            return SessionStore.user.value?.let { RepositoryResult.Success(it) }
                ?: RepositoryResult.Error("No active session. Please sign in first.")
        }

        return runCatching {
            apiService.getProfile("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val user = body?.user ?: SessionStore.user.value

                when {
                    response.isSuccessful && user != null -> {
                        SessionStore.updateUser(user)
                        RepositoryResult.Success(user)
                    }
                    body?.message?.isNotBlank() == true ->
                        RepositoryResult.Error(body.message)
                    else ->
                        RepositoryResult.Error("Unable to load profile.")
                }
            },
            onFailure = {
                SessionStore.user.value?.let { user ->
                    RepositoryResult.Success(user)
                }
                    ?: RepositoryResult.Error(it.message ?: "Profile request failed.")
            }
        )
    }

    suspend fun saveProfile(
        name: String,
        email: String,
        gender: String,
        weight: String,
        height: String,
        lastCheckupDate: String? = null
    ): RepositoryResult<UserDto> {
        val token = SessionStore.token.value 
            ?: return RepositoryResult.Error("No active session. Please sign in first.")

        return runCatching {
            val request = com.example.finalapp.data.model.ProfileUpdateRequest(
                name = name,
                email = email,
                gender = gender.ifBlank { null },
                weight = weight.ifBlank { null },
                height = height.ifBlank { null },
                lastCheckupDate = lastCheckupDate
            )
            apiService.updateProfile("Bearer $token", request)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                val updatedUser = body?.user

                if (response.isSuccessful && updatedUser != null) {
                    SessionStore.updateUser(updatedUser)
                    RepositoryResult.Success(updatedUser)
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to save profile.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while saving profile.")
            }
        )
    }
}
