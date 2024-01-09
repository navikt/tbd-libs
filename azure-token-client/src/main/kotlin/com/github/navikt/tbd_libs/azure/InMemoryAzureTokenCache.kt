package com.github.navikt.tbd_libs.azure

import java.util.concurrent.ConcurrentHashMap

class InMemoryAzureTokenCache(private val other: AzureTokenProvider) : AzureTokenProvider {
    private val cache = ConcurrentHashMap<String, AzureToken>()

    override fun bearerToken(scope: String): AzureToken {
        return cachedToken(scope) ?: lagreNy(scope)
    }

    private fun cachedToken(scope: String) = cache[scope]?.takeUnless { it.isExpired }
    private fun lagreNy(scope: String) = other.bearerToken(scope).also { cache[scope] = it }
}