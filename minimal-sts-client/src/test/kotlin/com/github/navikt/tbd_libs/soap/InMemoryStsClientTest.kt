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
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns token

        val cachedClient = InMemoryStsClient(kilde)
        val result = cachedClient.samlToken(USERNAME, PASSWORD)

        assertSame(token, result)
        verify(exactly = 1) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }

    @Test
    fun `henter fra cache om verdi ikke er utgått`() {
        val kilde = mockk<SamlTokenProvider>(relaxed = true)
        val token = SamlToken("<samltoken>", LocalDateTime.MAX)
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns token

        val cachedClient = InMemoryStsClient(kilde)
        val result1 = cachedClient.samlToken(USERNAME, PASSWORD) // første kall
        val result2 = cachedClient.samlToken(USERNAME, PASSWORD) // andre kall

        assertSame(token, result1)
        assertSame(result1, result2)
        verify(exactly = 1) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }

    @Test
    fun `henter fra kilde om verdi er utgått`() {
        val kilde = mockk<SamlTokenProvider>(relaxed = true)
        val token1 = SamlToken("<samltoken>", LocalDateTime.MIN)
        val token2 = SamlToken("<nytt samltoken>", LocalDateTime.MIN)
        every { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) } returns token1 andThen token2

        val cachedClient = InMemoryStsClient(kilde)
        val result1 = cachedClient.samlToken(USERNAME, PASSWORD) // første kall
        val result2 = cachedClient.samlToken(USERNAME, PASSWORD) // andre kall, oppfrisker

        assertSame(token1, result1)
        assertNotSame(result1, result2)
        assertSame(result2, result2)
        verify(exactly = 2) { kilde.samlToken(eq(USERNAME), eq(PASSWORD)) }
    }
}