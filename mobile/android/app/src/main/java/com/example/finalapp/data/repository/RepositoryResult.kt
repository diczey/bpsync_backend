package com.example.finalapp.data.repository

sealed interface RepositoryResult<out T> {
    data class Success<T>(val data: T) : RepositoryResult<T>
    data class Error(val message: String) : RepositoryResult<Nothing>
}
