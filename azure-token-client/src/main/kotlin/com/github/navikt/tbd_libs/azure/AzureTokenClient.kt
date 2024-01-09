package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.azure.AzureErrorResponse.Companion.azureErrorResponseOrNull
import com.github.navikt.tbd_libs.azure.AzureTokenResponse.Companion.azureTokenResponseOrNull
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
    private val jsonSerde: JsonSerde
) : AzureTokenProvider {

    override fun onBehalfOfToken(scope: String, token: String) =
        håndterTokenRespons(requestOnBehalfOfToken(scope, token))
    override fun bearerToken(scope: String) =
        håndterTokenRespons(requestBearerToken(scope))

    private fun håndterTokenRespons(body: String): AzureToken {
        val tokenResponse = jsonSerde.azureTokenResponseOrNull(body, LocalDateTime.now()) ?: kastExceptionVedFeil(body)
        return AzureToken(tokenResponse.token, tokenResponse.expirationTime)
    }

    private fun kastExceptionVedFeil(body: String): Nothing {
        val error = jsonSerde.azureErrorResponseOrNull(body)
            ?: throw AzureClientException("Ukjent feil ved henting av token. Kan ikke tolke responsen: $body")
        throw AzureClientException("Feil fra azure: ${error.error}: ${error.description}")
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