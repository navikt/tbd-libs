package com.github.navikt.tbd_libs.spurtedu

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

class SpurteDuClientTest {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    }

    @Test
    fun `veksler til 'on behalf of' token hvis det er satt`() {
        val (spurteDuClient, httpClient) = mockClient(okVisResponse)
        spurteDuClient.vis(UUID.randomUUID(), "et_bruker_token")
        verifiserRequestHeader(httpClient, "Authorization") { verdi ->
            verdi == "Bearer on_behalf_of_token"
        }
    }

    @Test
    fun `veksler til bearer token hvis obo-token ikke er satt`() {
        val (spurteDuClient, httpClient) = mockClient(okVisResponse)
        spurteDuClient.vis(UUID.randomUUID())
        verifiserRequestHeader(httpClient, "Authorization") { verdi ->
            verdi == "Bearer bearer_token"
        }
    }

    @Test
    fun `utveksle url`() {
        utveksle(SkjulRequest.SkjulUrlRequest("http://google.com")) { body ->
            !body.hasNonNull("tekst") && body.hasNonNull("url") && !body.hasNonNull("påkrevdTilgang")
        }
    }

    @Test
    fun `utveksle url med påkrevd tilgang`() {
        utveksle(SkjulRequest.SkjulUrlRequest("http://google.com", "en_konkret_person@nav.no")) { body ->
            !body.hasNonNull("tekst") && body.hasNonNull("url") && body.hasNonNull("påkrevdTilgang")
        }
    }

    @Test
    fun `utveksle tekst`() {
        utveksle(SkjulRequest.SkjulTekstRequest("""{ "foo": "bar" }""")) { body ->
            body.hasNonNull("tekst") && !body.hasNonNull("url") && !body.hasNonNull("påkrevdTilgang")
        }
    }

    @Test
    fun `utveksle tekst med påkrevd tilgang`() {
        utveksle(SkjulRequest.SkjulTekstRequest("""{ "foo": "bar" }""", "en_azure_ad_gruppe_id")) { body ->
            body.hasNonNull("tekst") && !body.hasNonNull("url") && body.hasNonNull("påkrevdTilgang")
        }
    }

    @Test
    fun metadata() {
        val (spurteDuClient, httpClient) = mockClient(okMetadataResponse)
        val secret = UUID.fromString("2d05217c-1c16-4581-b4e8-08e115a2274d")
        val response = spurteDuClient.metadata(secret)
        verifiserGET(httpClient)
        assertEquals(MetadataResponse(
            opprettet = OffsetDateTime.parse("2024-05-30T20:25:46.226344852+02:00")
        ), response)
    }

    @Test
    fun `vise skjult verdi - uten tilgang`() {
        val (spurteDuClient, httpClient) = mockClient(errorVisResponse, 404)
        val secret = UUID.fromString("2d05217c-1c16-4581-b4e8-08e115a2274d")
        assertThrows<SpurteDuException> { spurteDuClient.vis(secret, "et feilaktig token") }
        verifiserGET(httpClient)
    }

    @Test
    fun `vise skjult verdi - med tilgang`() {
        val (spurteDuClient, _) = mockClient(okVisResponse)
        val secret = UUID.fromString("2d05217c-1c16-4581-b4e8-08e115a2274d")
        val response = spurteDuClient.vis(secret, "et ok token")
        assertEquals("en jsonmelding", objectMapper.readTree(response.text).path("foo").asText())
    }

    private fun utveksle(payload: SkjulRequest, verifisering: (body: JsonNode) -> Boolean) {
        val (spurteDuClient, httpClient) = mockClient(okUtveksleResponse)

        val response = spurteDuClient.skjul(payload)

        verifiserPOST(httpClient)
        verifiserRequestBody(httpClient, verifisering)
        assertEquals(SkjulResponse(
            id = UUID.fromString("2d05217c-1c16-4581-b4e8-08e115a2274d"),
            url = "https://spurte-du/vis_meg/2d05217c-1c16-4581-b4e8-08e115a2274d",
            path = "/vis_meg/2d05217c-1c16-4581-b4e8-08e115a2274d"
        ), response)
    }

    private fun mockClient(response: String, statusCode: Int = 200): Pair<SpurteDuClient, HttpClient> {
        val httpClient = mockk<HttpClient> {
            every {
                send<String>(any(), any())
            } returns MockHttpResponse(response, statusCode)
        }
        val tokenProvider = object : AzureTokenProvider {
            override fun onBehalfOfToken(scope: String, token: String): AzureToken {
                return AzureToken("on_behalf_of_token", LocalDateTime.now())
            }

            override fun bearerToken(scope: String): AzureToken {
                return AzureToken("bearer_token", LocalDateTime.now())
            }
        }
        val spurteDuClient = SpurteDuClient(httpClient, objectMapper, tokenProvider)
        return spurteDuClient to httpClient
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
    private val okVisResponse = """{
    "text": "{ \"foo\": \"en jsonmelding\" }"
}"""

    private val errorVisResponse = """fant ikke noe spesielt til deg"""

    @Language("JSON")
    private val okMetadataResponse = """{
    "opprettet": "2024-05-30T20:25:46.226344852+02:00"
}"""

    @Language("JSON")
    private val okUtveksleResponse = """{
    "id": "2d05217c-1c16-4581-b4e8-08e115a2274d",
    "url": "https://spurte-du/vis_meg/2d05217c-1c16-4581-b4e8-08e115a2274d",
    "path": "/vis_meg/2d05217c-1c16-4581-b4e8-08e115a2274d"
}"""
}