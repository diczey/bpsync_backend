package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.LoginRequest
import com.example.finalapp.data.model.RegisterRequest
import com.example.finalapp.data.model.UserDto
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun login(email: String, password: String): RepositoryResult<UserDto?> {
        return runCatching {
            apiService.login(LoginRequest(email = email.trim(), password = password))
        }.fold(
            onSuccess = { response ->
                response.toAuthResult().also { result ->
                    if (result is RepositoryResult.Success) {
                        SessionStore.setSession(response.body()?.token, result.data)
                    }
                }
            },
            onFailure = { RepositoryResult.Error(it.message ?: "Login request failed.") }
        )
    }

    suspend fun register(request: RegisterRequest): RepositoryResult<UserDto?> {
        return runCatching {
            apiService.register(request)
        }.fold(
            onSuccess = { response ->
                response.toAuthResult().also { result ->
                    if (result is RepositoryResult.Success) {
                        SessionStore.setSession(response.body()?.token, result.data)
                    }
                }
            },
            onFailure = { RepositoryResult.Error(it.message ?: "Registration request failed.") }
        )
    }

    fun logout() {
        SessionStore.clear()
    }

    private fun Response<com.example.finalapp.data.model.LoginResponse>.toAuthResult(): RepositoryResult<UserDto?> {
        val body = body()
        return when {
            isSuccessful && body?.success == true -> RepositoryResult.Success(body.user)
            body?.message?.isNotBlank() == true -> RepositoryResult.Error(body.message)
            !isSuccessful -> RepositoryResult.Error("Request failed with HTTP ${code()}.")
            else -> RepositoryResult.Error("Unexpected empty response from server.")
        }
    }
}
