package com.github.navikt.tbd_libs.spedisjon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import com.github.navikt.tbd_libs.result_object.Result
import com.github.navikt.tbd_libs.result_object.ok
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

class SpedisjonClientTest {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Test
    fun `hent melding`() {
        val (spedisjonClient, httpClient) = mockClient(okResponse)

        val response = spedisjonClient.hentMelding(UUID.randomUUID())
        response as Result.Ok
        verifiserGET(httpClient)
        assertEquals("ny_søknad", response.value.type)
        assertEquals("fnr", response.value.fnr)
    }

    @Test
    fun `hent melding - feil`() {
        val (spedisjonClient, httpClient) = mockClient(errorResponse, 404)
        val result = spedisjonClient.hentMelding(UUID.randomUUID())
        result as Result.Error
        assertEquals("Feil fra Spedisjon (http 404): noe gikk galt", result.error)
        assertNull(result.cause)
        verifiserGET(httpClient)
    }

    private fun mockClient(response: String, statusCode: Int = 200): Pair<SpedisjonClient, HttpClient> {
        val httpClient = mockk<HttpClient> {
            every {
                send<String>(any(), any())
            } returns MockHttpResponse(response, statusCode)
        }
        val tokenProvider = object : AzureTokenProvider {
            override fun onBehalfOfToken(scope: String, token: String): Result<AzureToken> {
                return AzureToken("on_behalf_of_token", LocalDateTime.now()).ok()
            }

            override fun bearerToken(scope: String): Result<AzureToken> {
                return AzureToken("bearer_token", LocalDateTime.now()).ok()
            }
        }
        val spedisjonClient = SpedisjonClient(httpClient, objectMapper, tokenProvider)
        return spedisjonClient to httpClient
    }

    fun verifiserPOST(httpClient: HttpClient) {
        verifiserRequestMethod(httpClient, "POST")
    }

    fun verifiserGET(httpClient: HttpClient) {
        verifiserRequestMethod(httpClient, "GET")
    }

    fun verifiserRequestMethod(httpClient: HttpClient, method: String) {
        verify {
            httpClient.send<String>(match { request ->
                request.method().uppercase() == method.uppercase()
            }, any())
        }
    }

    fun verifiserRequestHeader(httpClient: HttpClient, headerName: String, verifisering: (String?) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(request.headers().firstValue(headerName).getOrNull())
            }, any())
        }
    }

    private fun verifiserRequestBody(httpClient: HttpClient, verifisering: (body: JsonNode) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(objectMapper.readTree(request.bodyAsString()))
            }, any())
        }
    }

    @Language("JSON")
    private val errorResponse = """{
  "type": "urn:error:internal_error",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "noe gikk galt",
  "instance": "/melding",
  "callId": "${UUID.randomUUID()}",
  "stacktrace": null
}"""

    @Language("JSON")
    private val okResponse = """{
  "type": "ny_søknad",
  "fnr": "fnr",
  "internDokumentId": "${UUID.randomUUID()}",
  "eksternDokumentId": "${UUID.randomUUID()}",
  "rapportertDato": "${LocalDateTime.now()}",
  "duplikatkontroll": "unik_nøkkel",
  "jsonBody": "{ \"id\": \"${UUID.randomUUID()}\", \"status\": \"NY\" }"
}"""
}