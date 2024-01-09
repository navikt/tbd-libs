package com.github.navikt.tbd_libs.azure

interface AzureTokenProvider {
    fun bearerToken(scope: String): AzureToken
}