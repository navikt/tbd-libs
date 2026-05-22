package com.github.navikt.tbd_libs.access_token

import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import com.github.navikt.tbd_libs.retry.PredefinerteUtsettelser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TexasClientTest {

    private companion object {
        val TOKEN_ENDPOINT = URI("http://texas/api/v1/token")
        val EXCHANGE_ENDPOINT = URI("http://texas/api/v1/token/exchange")
        val INGEN_FORSINKELSE = { PredefinerteUtsettelser(Duration.ZERO, Duration.ZERO, Duration.ZERO) }

        const val SCOPE = "api://cluster.namespace.app/.default"
        const val USER_TOKEN = "eyJuser..."
        const val ACCESS_TOKEN = "eyJaccess..."

        val OK_RESPONS = """{"access_token":"$ACCESS_TOKEN","token_type":"Bearer","expires_in":3599}"""
        val FEIL_RESPONS = """{"error":"invalid_scope","error_description":"Ugyldig scope"}"""

        fun client(httpClient: HttpClient) = TexasClient(
            tokenEndpoint = TOKEN_ENDPOINT,
            tokenExchangeEndpoint = EXCHANGE_ENDPOINT,
            httpClient = httpClient,
            utsettelser = INGEN_FORSINKELSE
        )
    }

    @Test
    fun `machineToken sender korrekt forespørsel og returnerer token`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } returns MockHttpResponse(OK_RESPONS, 200)
        }
        val token = client(httpClient).machineToken(SCOPE)

        assertEquals(ACCESS_TOKEN, token)
        verify(exactly = 1) {
            httpClient.send<String>(match { req ->
                req.uri() == TOKEN_ENDPOINT &&
                req.method() == "POST" &&
                req.bodyAsString().contains(""""target":"$SCOPE"""") &&
                req.bodyAsString().contains(""""identity_provider":"entra_id"""")
            }, any())
        }
    }

    @Test
    fun `oboToken sender korrekt forespørsel med user_token og returnerer token`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } returns MockHttpResponse(OK_RESPONS, 200)
        }
        val token = client(httpClient).oboToken(USER_TOKEN, SCOPE)

        assertEquals(ACCESS_TOKEN, token)
        verify(exactly = 1) {
            httpClient.send<String>(match { req ->
                req.uri() == EXCHANGE_ENDPOINT &&
                req.method() == "POST" &&
                req.bodyAsString().contains(""""target":"$SCOPE"""") &&
                req.bodyAsString().contains(""""identity_provider":"entra_id"""") &&
                req.bodyAsString().contains(""""user_token":"$USER_TOKEN"""")
            }, any())
        }
    }

    @Test
    fun `4xx kaster AccessTokenException uten retry`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } returns MockHttpResponse(FEIL_RESPONS, 400)
        }
        assertThrows<AccessTokenException> { client(httpClient).machineToken(SCOPE) }
        verify(exactly = 1) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `5xx retryes og kaster AccessTokenException etter alle forsøk`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } returns MockHttpResponse("""{"error":"internal"}""", 500)
        }
        assertThrows<AccessTokenException> { client(httpClient).machineToken(SCOPE) }
        // DefaultUtsettelser har 3 forsinkelser → 4 forsøk totalt
        verify(exactly = 4) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `5xx retryes og returnerer token ved suksess på andre forsøk`() {
        val httpClient = mockk<HttpClient>()
        every { httpClient.send<String>(any(), any()) } returnsMany listOf(
            MockHttpResponse("""{"error":"temporarily unavailable"}""", 503),
            MockHttpResponse(OK_RESPONS, 200)
        )
        val token = client(httpClient).machineToken(SCOPE)
        assertEquals(ACCESS_TOKEN, token)
        verify(exactly = 2) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `nettverksfeil retryes og kaster AccessTokenException etter alle forsøk`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } throws RuntimeException("Connection refused")
        }
        assertThrows<AccessTokenException> { client(httpClient).machineToken(SCOPE) }
        verify(exactly = 4) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `oboToken 4xx retryes ikke`() {
        val httpClient = mockk<HttpClient> {
            every { send<String>(any(), any()) } returns MockHttpResponse(FEIL_RESPONS, 401)
        }
        assertThrows<AccessTokenException> { client(httpClient).oboToken(USER_TOKEN, SCOPE) }
        verify(exactly = 1) { httpClient.send<String>(any(), any()) }
    }
}
