package com.github.navikt.tbd_libs.result_object

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Error(val error: String, val cause: Throwable? = null) : Result<Nothing>
}

fun <T, R> Result<T>.map(whenOk: (T) -> Result<R>) = when (this) {
    is Result.Error -> this
    is Result.Ok -> whenOk(value)
}

fun <T, R> Result<T>.fold(
    whenOk: (T) -> R,
    whenError: (String, Throwable?) -> R
) = when (this) {
    is Result.Error -> whenError(error, cause)
    is Result.Ok -> whenOk(value)
}

fun <T> List<Result<T>>.flatten(): Result<List<T>> {
    return fold(Result.Ok(emptyList<T>()) as Result<List<T>>) { acc, result ->
        result.fold(
            whenOk = { personResponse ->
                acc.map { list ->
                    list.plusElement(personResponse).ok()
                }
            },
            whenError = { msg, cause ->
                msg.error(cause)
            }
        )
    }
}

fun <T> T.ok() = Result.Ok(this)
fun Throwable.error(message: String) = Result.Error(message, this)
fun String.error(cause: Throwable? = null) = Result.Error(this, cause)