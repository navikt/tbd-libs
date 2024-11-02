package com.github.navikt.tbd_libs.azure

interface AzureTokenProvider {
    fun onBehalfOfToken(scope: String, token: String): AzureTokenResult
    fun bearerToken(scope: String): AzureTokenResult

    sealed interface AzureTokenResult {
        data class Ok(val azureToken: AzureToken) : AzureTokenResult
        data class Error(val error: String, val exception: Throwable? = null) : AzureTokenResult
    }
}