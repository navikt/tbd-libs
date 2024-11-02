package com.github.navikt.tbd_libs.soap

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class InMemoryStsClientTest {
    private companion object {
        private const val USERNAME = "foo"
        private const val PASSWORD = "bar"
    }

    @Test
    fun `henter fra kilde om cache er tom`() {
        val kilde = mockk<SamlTokenProvider>(relaxed = true)
        val token = SamlToken("<samltoken>", LocalDateTime.MAX)
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns SamlTokenProvider.SamlTokenResult.Ok(token)

        val cachedClient = InMemoryStsClient(kilde)
        val result = cachedClient.samlToken(USERNAME, PASSWORD)
        result as SamlTokenProvider.SamlTokenResult.Ok
        assertSame(token, result.token)
        verify(exactly = 1) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }

    @Test
    fun `henter fra cache om verdi ikke er utgått`() {
        val kilde = mockk<SamlTokenProvider>(relaxed = true)
        val token = SamlToken("<samltoken>", LocalDateTime.MAX)
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns SamlTokenProvider.SamlTokenResult.Ok(token)

        val cachedClient = InMemoryStsClient(kilde)
        val result1 = cachedClient.samlToken(USERNAME, PASSWORD) // første kall
        val result2 = cachedClient.samlToken(USERNAME, PASSWORD) // andre kall
        result1 as SamlTokenProvider.SamlTokenResult.Ok
        result2 as SamlTokenProvider.SamlTokenResult.Ok
        assertSame(token, result1.token)
        assertSame(result1.token, result2.token)
        verify(exactly = 1) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }

    @Test
    fun `henter fra kilde om verdi er utgått`() {
        val kilde = mockk<SamlTokenProvider>(relaxed = true)
        val token1 = SamlToken("<samltoken>", LocalDateTime.MIN)
        val token2 = SamlToken("<nytt samltoken>", LocalDateTime.MIN)
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns SamlTokenProvider.SamlTokenResult.Ok(token1) andThen SamlTokenProvider.SamlTokenResult.Ok(token2)

        val cachedClient = InMemoryStsClient(kilde)
        val result1 = cachedClient.samlToken(USERNAME, PASSWORD) // første kall
        val result2 = cachedClient.samlToken(USERNAME, PASSWORD) // andre kall, oppfrisker

        result1 as SamlTokenProvider.SamlTokenResult.Ok
        result2 as SamlTokenProvider.SamlTokenResult.Ok

        assertSame(token1, result1.token)
        assertNotSame(result1.token, result2.token)
        assertSame(result2.token, result2.token)
        verify(exactly = 2) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }
}