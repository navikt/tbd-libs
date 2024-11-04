package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.result_object.Result

interface AzureTokenProvider {
    fun onBehalfOfToken(scope: String, token: String): Result<AzureToken>
    fun bearerToken(scope: String): Result<AzureToken>
}