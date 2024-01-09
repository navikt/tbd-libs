package com.github.navikt.tbd_libs.azure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class InMemoryAzureTokenCacheTest {

    @Test
    fun `henter verdi når cache er tom`() {
        val mock = mockk<AzureTokenProvider>(relaxed = true)
        val cache = InMemoryAzureTokenCache(mock)
        val scope = "testscope"
        cache.bearerToken(scope)
        verify(exactly = 1) { mock.bearerToken(scope) }
    }
    @Test
    fun `henter ikke verdi når cache finnes`() {
        val mock = mockk<AzureTokenProvider>(relaxed = true)
        val cache = InMemoryAzureTokenCache(mock)
        val scope = "testscope"
        cache.bearerToken(scope) // første kall
        cache.bearerToken(scope) // andre kall, går via cache
        verify(exactly = 1) { mock.bearerToken(scope) }
    }

    @Test
    fun `henter ikke verdi når cache er utdatert`() {
        val mock = mockk<AzureTokenProvider>(relaxed = true)
        every {
            mock.bearerToken(any())
        } returns AzureToken("access_token", LocalDateTime.now().minusSeconds(1))
        val cache = InMemoryAzureTokenCache(mock)
        val scope = "testscope"
        cache.bearerToken(scope) // første kall
        cache.bearerToken(scope) // andre kall, går ikke via cache
        verify(exactly = 2) { mock.bearerToken(scope) }
    }

    @Test
    fun `henter obo token når cache er tom`() {
        val mock = mockk<AzureTokenProvider>(relaxed = true)
        val cache = InMemoryAzureTokenCache(mock)
        val scope = "testscope"
        cache.onBehalfOfToken(scope, "ett token")
        cache.onBehalfOfToken(scope, "to token")
        verify(exactly = 2) { mock.onBehalfOfToken(scope, any()) }
    }

    @Test
    fun `henter obo token fra cache`() {
        val mock = mockk<AzureTokenProvider>(relaxed = true)
        val cache = InMemoryAzureTokenCache(mock)
        val scope = "testscope"
        val result1 = cache.onBehalfOfToken(scope, "ett token")
        cache.onBehalfOfToken(scope, "to token")
        val result2 = cache.onBehalfOfToken(scope, "ett token")
        verify(exactly = 1) { mock.onBehalfOfToken(scope, "ett token") }
        assertSame(result1, result2)
    }
}