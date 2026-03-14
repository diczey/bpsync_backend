package com.example.bp.data.repository

import com.example.bp.data.api.ApiService
import com.example.bp.data.model.LoginRequest
import com.example.bp.data.model.RegisterRequest
import com.example.bp.data.model.LoginResponse

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class AuthRepository(private val api: ApiService) {

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.token != null) {
                    Result.Success(body)
                } else {
                    Result.Error(body.message ?: "Giriş başarısız")
                }
            } else {
                val error = response.errorBody()?.string() ?: "Giriş başarısız"
                Result.Error(error)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası. Lütfen ağ bağlantınızı kontrol edin.")
        }
    }

    suspend fun register(request: RegisterRequest): Result<LoginResponse> {
        return try {
            val response = api.register(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.token != null) {
                    Result.Success(body)
                } else {
                    Result.Error(body.message ?: "Kayıt başarısız")
                }
            } else {
                val error = response.errorBody()?.string() ?: "Kayıt başarısız"
                Result.Error(error)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }
}
