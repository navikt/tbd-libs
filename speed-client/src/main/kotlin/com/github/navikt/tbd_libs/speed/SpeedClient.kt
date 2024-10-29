package com.github.navikt.tbd_libs.speed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

class SpeedClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    baseUrl: String? = null,
    scope: String? = null
) {
    private val baseUrl = baseUrl ?: "http://speed-api"
    private val scope = scope ?: "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.speed-api/.default"

    fun hentFødselsnummerOgAktørId(ident: String, callId: String = UUID.randomUUID().toString()): IdentResponse {
        val token = tokenProvider.bearerToken(scope)
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/api/ident"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.token}")
            .header("callId", callId)
            .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            val feilmelding = convertResponseBody<IdentFeilresponse>(response)
            throw SpeedException("Feil fra Speed: ${feilmelding.feilmelding}")
        }
        return convertResponseBody(response)
    }

    private inline fun <reified T> convertResponseBody(response: HttpResponse<String>): T {
        return try {
            objectMapper.readValue<T>(response.body())
        } catch (err: Exception) {
            throw SpeedException(err.message ?: "JSON parsing error", err)
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentRequest(val ident: String)
@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentFeilresponse(val feilmelding: String)
@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentResponse(
    val fødselsnummer: String,
    val aktørId: String,
    val npid: String?,
    val kilde: KildeResponse
) {
    enum class KildeResponse {
        CACHE, PDL
    }
}
class SpeedException(override val message: String, override val cause: Throwable? = null) : RuntimeException()

