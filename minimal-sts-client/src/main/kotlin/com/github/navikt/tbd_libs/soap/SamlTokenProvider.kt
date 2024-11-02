package com.github.navikt.tbd_libs.soap

interface SamlTokenProvider {
    fun samlToken(username: String, password: String): SamlTokenResult

    sealed interface SamlTokenResult {
        data class Ok(val token: SamlToken) : SamlTokenResult
        data class Error(val error: String, val exception: Throwable? = null) : SamlTokenResult
    }
}