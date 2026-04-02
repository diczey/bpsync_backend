package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.LoginRequest
import com.example.finalapp.data.model.RegisterRequest
import com.example.finalapp.data.model.UserDto
import org.json.JSONObject
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
                        ReadingRepository.clear()
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
                        ReadingRepository.clear()
                        SessionStore.setSession(response.body()?.token, result.data)
                    }
                }
            },
            onFailure = { RepositoryResult.Error(it.message ?: "Registration request failed.") }
        )
    }

    fun logout() {
        ReadingRepository.clear()
        SessionStore.clear()
    }

    private fun Response<com.example.finalapp.data.model.LoginResponse>.toAuthResult(): RepositoryResult<UserDto?> {
        val body = body()
        return when {
            isSuccessful && body?.success == true -> RepositoryResult.Success(body.user)
            body?.message?.isNotBlank() == true -> RepositoryResult.Error(body.message)
            !isSuccessful -> RepositoryResult.Error(extractErrorMessage() ?: "Request failed with HTTP ${code()}.")
            else -> RepositoryResult.Error("Unexpected empty response from server.")
        }
    }

    private fun Response<com.example.finalapp.data.model.LoginResponse>.extractErrorMessage(): String? {
        val errorText = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null

        return runCatching {
            val root = JSONObject(errorText)

            root.optString("message")
                .takeIf { it.isNotBlank() }
                ?: root.optJSONArray("detail")
                    ?.let { details ->
                        buildList {
                            for (index in 0 until details.length()) {
                                val detail = details.optJSONObject(index) ?: continue
                                val loc = detail.optJSONArray("loc")
                                val fieldName = if (loc != null && loc.length() > 1) {
                                    loc.optString(1)
                                        .replace('_', ' ')
                                        .replaceFirstChar { it.titlecase() }
                                } else {
                                    null
                                }
                                val message = detail.optString("msg").takeIf { it.isNotBlank() }

                                when {
                                    fieldName != null && message != null -> add("$fieldName: $message")
                                    message != null -> add(message)
                                }
                            }
                        }.joinToString("\n").takeIf { it.isNotBlank() }
                    }
        }.getOrNull()
    }
}
