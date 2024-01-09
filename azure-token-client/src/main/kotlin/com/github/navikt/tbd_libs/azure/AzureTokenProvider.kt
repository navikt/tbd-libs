package com.github.navikt.tbd_libs.azure

interface AzureTokenProvider {
    fun onBehalfOfToken(scope: String, token: String): AzureToken
    fun bearerToken(scope: String): AzureToken
}