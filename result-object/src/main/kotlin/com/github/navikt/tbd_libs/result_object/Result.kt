package com.github.navikt.tbd_libs.result_object

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Error(val error: String, val cause: Throwable? = null) : Result<Nothing>
}

fun <T, R> Result<T>.map(whenOk: (T) -> Result<R>) = when (this) {
    is Result.Error -> this
    is Result.Ok -> whenOk(value)
}

fun <T> T.ok() = Result.Ok(this)
fun Throwable.error(message: String) = Result.Error(message, this)
fun String.error(cause: Throwable? = null) = Result.Error(this, cause)