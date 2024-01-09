package com.github.navikt.tbd_libs.azure

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class InMemoryAzureTokenCache(private val other: AzureTokenProvider) : AzureTokenProvider {
    private val cache = ConcurrentHashMap<String, AzureToken>()

    override fun onBehalfOfToken(scope: String, token: String) =
        fraCacheEllerHentNy(oboCacheKey(token, scope)) { other.onBehalfOfToken(scope, token) }

    override fun bearerToken(scope: String) =
        fraCacheEllerHentNy(scope) { other.bearerToken(scope) }

    private fun fraCacheEllerHentNy(cacheKey: String, hentNy: () -> AzureToken) =
        cachedToken(cacheKey) ?: lagreNy(cacheKey, hentNy())

    private fun cachedToken(cacheKey: String) = cache[cacheKey]?.takeUnless { it.isExpired }
    private fun lagreNy(cacheKey: String, token: AzureToken) = token.also { cache[cacheKey] = it }


    @OptIn(ExperimentalStdlibApi::class)
    private fun oboCacheKey(token: String, scope: String): String {
        val nøkkel = "${token}${scope}".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(nøkkel)
        return digest.toHexString()
    }
}