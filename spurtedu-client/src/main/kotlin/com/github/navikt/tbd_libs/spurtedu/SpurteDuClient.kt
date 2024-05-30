package com.github.navikt.tbd_libs.spurtedu

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class SpurteDuClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    baseUrl: String? = null,
    scope: String? = null
) {
    private val baseUrl = baseUrl ?: "http://spurtedu"
    private val scope = scope ?: "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spurtedu/.default"

    fun skjul(payload: SkjulRequest): SkjulResponse {
        val jsonInputString = objectMapper.writeValueAsString(payload)
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/skjul_meg"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return convertResponseBody(response)
    }

    fun vis(id: UUID, onBehalfOfToken: String? = null, callId: UUID = UUID.randomUUID()): VisTekstResponse {
        val token = onBehalfOfToken?.let { tokenProvider.onBehalfOfToken(scope, onBehalfOfToken) } ?: tokenProvider.bearerToken(scope)
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/vis_meg/$id"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.token}")
            .header("callId", "$callId")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw SpurteDuException("Finner ikke hemmeligheten, mulig det er krøll med tilganger (status code ${response.statusCode()})")
        return convertResponseBody(response)
    }

    fun metadata(id: UUID): MetadataResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/vis_meg/$id/metadata"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return convertResponseBody(response)
    }

    private inline fun <reified T> convertResponseBody(response: HttpResponse<String>): T {
        return try {
            objectMapper.readValue<T>(response.body())
        } catch (err: JsonParseException) {
            throw SpurteDuException(err.message ?: "JSON parsing error", err)
        }
    }
}

sealed class SkjulRequest {
    abstract val påkrevdTilgang: String?

    data class SkjulUrlRequest(val url: String, override val påkrevdTilgang: String? = null) : SkjulRequest()
    data class SkjulTekstRequest(val tekst: String, override val påkrevdTilgang: String? = null) : SkjulRequest()
}

data class MetadataResponse(
    val opprettet: OffsetDateTime
)
data class SkjulResponse(
    val id: UUID,
    val url: String,
    val path: String
)
data class VisTekstResponse(val text: String)
class SpurteDuException(override val message: String, override val cause: Throwable? = null) : RuntimeException()

