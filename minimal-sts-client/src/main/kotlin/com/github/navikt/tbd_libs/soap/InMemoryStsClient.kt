package com.github.navikt.tbd_libs.soap

import java.util.concurrent.ConcurrentHashMap

class InMemoryStsClient(private val other: SamlTokenProvider) : SamlTokenProvider by (other) {
    private val cache = ConcurrentHashMap<String, SamlToken>()

    override fun samlToken(username: String, password: String): SamlToken {
        return retrieveFromCache(username) ?: storeInCache(username, password)
    }

    private fun retrieveFromCache(username: String) =
        cache[username]?.takeUnless(SamlToken::isExpired)

    private fun storeInCache(username: String, password: String): SamlToken {
        return other.samlToken(username, password).also {
            cache[username] = it
        }
    }
}