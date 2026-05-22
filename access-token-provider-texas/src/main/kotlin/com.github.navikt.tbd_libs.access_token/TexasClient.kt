package com.github.navikt.tbd_libs.access_token

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.retry.DefaultUtsettelser
import com.github.navikt.tbd_libs.retry.retryBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Implementasjon av [AccessTokenProvider] som henter tokens via Texas (Token Exchange as a Service)
 * som kjører som en sidecar på NAIS-plattformen.
 *
 * Kall mot Texas retryes automatisk ved forbigående feil (nettverksfeil eller 5xx-svar).
 * Klientfeil (4xx) retryes ikke.
 *
 * @param tokenEndpoint URL til Texas maskin-til-maskin-tokenendepunkt
 *   (typisk fra miljøvariabelen `NAIS_TOKEN_ENDPOINT`).
 * @param tokenExchangeEndpoint URL til Texas OBO-token-vekslingsendepunkt
 *   (typisk fra miljøvariabelen `NAIS_TOKEN_EXCHANGE_ENDPOINT`).
 * @param httpClient Den underliggende HTTP-klienten som brukes for forespørsler.
 * @param utsettelser Utsettelser for retryer.
 */
class TexasClient(
    private val tokenEndpoint: URI,
    private val tokenExchangeEndpoint: URI,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val utsettelser: () -> Iterator<Duration> = ::DefaultUtsettelser
) : AccessTokenProvider {

    private val objectMapper = jacksonObjectMapper()

    /**
     * Henter et maskin-til-maskin bearer token for [scope]
     * via Texas `/api/v1/token`-endepunktet med Entra ID som identity provider.
     */
    override fun machineToken(scope: String): String {
        return runCatching {
            retryBlocking(utsettelser = utsettelser(), avbryt = ::ikkeRetrye) {
                post(tokenEndpoint, TokenRequest(target = scope))
            }
        }.getOrElse {
            throw AccessTokenException(it.message ?: "Uventet feil oppstod")
        }
    }

    /**
     * Veksler inn [accessToken] mot et On-Behalf-Of (OBO) bearer-token scoped til [scope]
     * via Texas `/api/v1/token/exchange`-endepunktet med Entra ID som identity provider.
     */
    override fun oboToken(accessToken: String, scope: String): String {
        return runCatching {
            retryBlocking(utsettelser = utsettelser(), avbryt = ::ikkeRetrye) {
                post(tokenExchangeEndpoint, TokenExchangeRequest(target = scope, userToken = accessToken))
            }
        }.getOrElse {
            throw AccessTokenException(it.message ?: "Uventet feil oppstod")
        }
    }

    private fun post(endpoint: URI, tokenRequest: TokenRequest): String {
        val request = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(tokenRequest)))
            .build()

        val response = httpClient.send(request, BodyHandlers.ofString())
        val responseBody = response.body()
            ?: throw TexasException("Tom responskropp fra Texas (HTTP ${response.statusCode()})")

        if (response.statusCode() in 400..499) {
            val error = runCatching { objectMapper.readValue<TokenErrorResponse>(responseBody) }.getOrNull()
            throw TexasClientException(
                "Klientfeil fra Texas (HTTP ${response.statusCode()}): ${error?.error ?: responseBody}"
            )
        }

        if (response.statusCode() !in 200..299) {
            val error = runCatching { objectMapper.readValue<TokenErrorResponse>(responseBody) }.getOrNull()
            throw TexasException(
                "Feil fra Texas (HTTP ${response.statusCode()}): ${error?.error ?: responseBody}"
            )
        }

        return objectMapper.readValue<TokenResponse>(responseBody).accessToken
    }

    companion object {
        /**
         * Oppretter en [TexasClient] ved å lese endepunkter fra NAIS-miljøvariabler:
         * - `NAIS_TOKEN_ENDPOINT` for maskin-til-maskin-tokens
         * - `NAIS_TOKEN_EXCHANGE_ENDPOINT` for OBO-token-veksling
         */
        fun fromEnv(httpClient: HttpClient = HttpClient.newHttpClient()): TexasClient {
            val tokenEndpoint = requireNotNull(System.getenv("NAIS_TOKEN_ENDPOINT")) {
                "Miljøvariabelen NAIS_TOKEN_ENDPOINT er ikke satt"
            }
            val tokenExchangeEndpoint = requireNotNull(System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT")) {
                "Miljøvariabelen NAIS_TOKEN_EXCHANGE_ENDPOINT er ikke satt"
            }
            return TexasClient(URI(tokenEndpoint), URI(tokenExchangeEndpoint), httpClient)
        }

        private fun ikkeRetrye(t: Throwable) = t is TexasClientException
    }
}

private open class TokenRequest(
    val target: String,
) {
    @JsonProperty("identity_provider")
    val identityProvider: String = "entra_id"
}

private class TokenExchangeRequest(
    target: String,
    @param:JsonProperty("user_token")
    val userToken: String
): TokenRequest(target) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenResponse(
    @param:JsonProperty("access_token")
    val accessToken: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TokenErrorResponse(
    val error: String
)

/** Kastes ved forbigående feil mot Texas (nettverksfeil, 5xx). Retryes automatisk. */
private open class TexasException(message: String) : RuntimeException(message)

/** Kastes ved klientfeil mot Texas (4xx). Retryes ikke. */
private class TexasClientException(message: String) : TexasException(message)
