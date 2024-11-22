package com.github.navikt.tbd_libs.spedisjon

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.Result
import com.github.navikt.tbd_libs.result_object.error
import com.github.navikt.tbd_libs.result_object.fold
import com.github.navikt.tbd_libs.result_object.map
import com.github.navikt.tbd_libs.result_object.ok
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SpedisjonClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    baseUrl: String? = null,
    scope: String? = null
) {
    private val baseUrl = baseUrl ?: "http://spedisjon"
    private val scope = scope ?: "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spedisjon/.default"

    fun hentMelding(internDokumentId: UUID, callId: String = UUID.randomUUID().toString()): Result<HentMeldingResponse> {
        return tokenProvider.bearerToken(scope)
            .map { token ->
                request("GET", "/api/melding/$internDokumentId", token, null, callId)
            }
            .map { response ->
                håndterRespons(response) { status, body ->
                    when (status) {
                        200 -> convertResponseBody<HentMeldingResponse>(body)
                        else -> {
                            convertResponseBody<Feilresponse>(body).fold(
                                whenOk = { "Feil fra Spedisjon (http $status): ${it.detail}".error() },
                                whenError = { msg, cause -> "Klarte ikke tolke feilrespons fra Spedisjon (http $status): $msg".error(cause) }
                            )
                        }
                    }
                }
            }
    }

    fun hentMeldinger(internDokumentIder: List<UUID>, callId: String = UUID.randomUUID().toString()): Result<HentMeldingerResponse> {
        return tokenProvider.bearerToken(scope)
            .map { token ->
                val jsonInputString = objectMapper.writeValueAsString(HentMeldingerRequest(internDokumentIder))
                request("POST", "/api/meldinger", token, jsonInputString, callId)
            }
            .map { response ->
                håndterRespons(response) { status, body ->
                    when (status) {
                        200 -> convertResponseBody<HentMeldingerResponse>(body)
                        else -> {
                            convertResponseBody<Feilresponse>(body).fold(
                                whenOk = { "Feil fra Spedisjon (http $status): ${it.detail}".error() },
                                whenError = { msg, cause -> "Klarte ikke tolke feilrespons fra Spedisjon (http $status): $msg".error(cause) }
                            )
                        }
                    }
                }
            }
    }

    private fun <T> håndterRespons(response: HttpResponse<String>, mapRespons: (status: Int, body: String) -> Result<T>): Result<T> {
        val body: String? = response.body()
        if (body == null) return "Tom responskropp fra spedisjon".error()
        return mapRespons(response.statusCode(), body)
    }

    private fun request(method: String, action: String, bearerToken: AzureToken, jsonInputString: String?, callId: String): Result<HttpResponse<String>> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl$action"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${bearerToken.token}")
                .header("callId", callId)
                .method(method, jsonInputString?.let { BodyPublishers.ofString(jsonInputString) } ?: BodyPublishers.noBody())
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString()).ok()
        } catch (err: Exception) {
            err.error("Feil ved sending av http request")
        }
    }

    private inline fun <reified T> convertResponseBody(response: String): Result<T> {
        return try {
            objectMapper.readValue<T>(response).ok()
        } catch (err: Exception) {
            err.error(err.message ?: "JSON parsing error")
        }
    }
}

internal data class HentMeldingerRequest(
    val internDokumentIder: List<UUID>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Feilresponse(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: URI,
    val callId: String?,
    val stacktrace: String? = null
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HentMeldingerResponse(
    val meldinger: List<HentMeldingResponse>
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HentMeldingResponse(
    val type: String,
    val fnr: String,
    val internDokumentId: UUID,
    val eksternDokumentId: UUID,
    val rapportertDato: LocalDateTime,
    val duplikatkontroll: String,
    val jsonBody: String
)