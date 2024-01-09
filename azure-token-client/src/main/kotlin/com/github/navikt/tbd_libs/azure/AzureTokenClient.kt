package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime

class AzureTokenClient(
    private val tokenEndpoint: URI,
    private val clientId: String,
    private val authMethod: AzureAuthMethod,
    private val client: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())
) : AzureTokenProvider {

    override fun onBehalfOfToken(scope: String, token: String) =
        håndterTokenRespons(requestOnBehalfOfToken(scope, token))
    override fun bearerToken(scope: String) =
        håndterTokenRespons(requestBearerToken(scope))

    private fun håndterTokenRespons(body: String): AzureToken {
        val tokenResponse = deserializeToken(body) ?: kastExceptionVedFeil(body)
        return AzureToken(tokenResponse.token, tokenResponse.expirationTime)
    }

    private fun kastExceptionVedFeil(body: String): Nothing {
        val error = deserializeErrorResponse(body)
            ?: throw AzureClientException("Ukjent feil ved henting av token. Kan ikke tolke responsen: $body")
        throw AzureClientException("Feil fra azure: ${error.error}: ${error.description}")
    }

    private fun deserializeErrorResponse(body: String): AzureErrorResponse? {
        return try {
            objectMapper.readValue<AzureErrorResponse>(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun deserializeToken(body: String): AzureTokenResponse? {
        val reader = objectMapper.reader(InjectableValues.Std()
            .addValue(LocalDateTime::class.java, LocalDateTime.now())
        ).forType(AzureTokenResponse::class.java)
        return try {
            reader.readValue<AzureTokenResponse>(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestBearerToken(scope: String) = requestToken(buildTokenRequestBody(scope))
    private fun requestOnBehalfOfToken(scope: String, token: String) = requestToken(buildOnBehalfOfRequestBody(scope, token))

    private fun requestToken(body: String): String {
        val request = HttpRequest.newBuilder(tokenEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body() ?: throw AzureClientException("Tom responskropp fra Azure")
    }
    private fun buildTokenRequestBody(scope: String): String {
        return buildRequestBody(scope, "client_credentials")
    }
    private fun buildOnBehalfOfRequestBody(scope: String, token: String): String {
        return buildRequestBody(scope, "urn:ietf:params:oauth:grant-type:jwt-bearer", mapOf(
            "requested_token_use" to "on_behalf_of",
            "assertion" to token
        ))
    }
    private fun buildRequestBody(scope: String, grantType: String, additionalPayload: Map<String, String> = emptyMap()): String {
        val standardPayload = mapOf(
            "client_id" to clientId,
            "scope" to scope,
            "grant_type" to grantType
        )
        return (standardPayload + additionalPayload + authMethod.requestParameters()).entries
            .joinToString(separator = "&") { (k, v) -> "$k=$v" }
    }
}

class AzureToken(
    val token: String,
    val expirationTime: LocalDateTime
) {
    private companion object {
        private val EXPIRATION_MARGIN = Duration.ofSeconds(10)
    }
    val isExpired get() = expirationTime <= LocalDateTime.now().plus(EXPIRATION_MARGIN)
}