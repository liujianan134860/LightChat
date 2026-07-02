package com.lightchat.core.network

sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>
    data class HttpError(val code: Int, val message: String) : NetworkResult<Nothing>
    data class TransportError(val cause: Throwable) : NetworkResult<Nothing>
    data class SerializationError(val cause: Throwable) : NetworkResult<Nothing>
}
