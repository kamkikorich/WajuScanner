package com.example.wajuscanner.core.common

sealed class Result<out T> {
    data object Idle : Result<Nothing>()
    data object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success)?.data
fun <T> Result<T>.errorMessageOrNull(): String? = (this as? Result.Error)?.message
