package com.github.navikt.tbd_libs.soap

import com.github.navikt.tbd_libs.soap.SamlTokenProvider.SamlTokenResult
import java.util.concurrent.ConcurrentHashMap

class InMemoryStsClient(private val other: SamlTokenProvider) : SamlTokenProvider by (other) {
    private val cache = ConcurrentHashMap<String, SamlToken>()

    override fun samlToken(username: String, password: String): SamlTokenResult {
        return retrieveFromCache(username) ?: storeInCache(username, password)
    }

    private fun retrieveFromCache(username: String) =
        cache[username]?.takeUnless(SamlToken::isExpired)?.let { SamlTokenResult.Ok(it) }

    private fun storeInCache(username: String, password: String): SamlTokenResult {
        return other.samlToken(username, password).also {
            if (it is SamlTokenResult.Ok) cache[username] = it.token
        }
    }
}