package com.github.navikt.tbd_libs.speed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.Result
import com.github.navikt.tbd_libs.result_object.error
import com.github.navikt.tbd_libs.result_object.map
import com.github.navikt.tbd_libs.result_object.ok
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
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

    fun hentFødselsnummerOgAktørId(ident: String, callId: String = UUID.randomUUID().toString()): Result<IdentResponse> {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        return postRequest("/api/ident", jsonInputString, callId).map {
            convertResponseBody<IdentResponse>(it)
        }
    }

    fun hentPersoninfo(ident: String, callId: String = UUID.randomUUID().toString()): Result<PersonResponse> {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        return postRequest("/api/person", jsonInputString, callId).map {
            convertResponseBody<PersonResponse>(it)
        }
    }

    fun hentHistoriskeFødselsnumre(ident: String, callId: String = UUID.randomUUID().toString()): Result<HistoriskeIdenterResponse> {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        return postRequest("/api/historiske_identer", jsonInputString, callId).map {
            convertResponseBody<HistoriskeIdenterResponse>(it)
        }
    }

    fun tømMellomlager(identer: Collection<String>, callId: String = UUID.randomUUID().toString()) {
        val jsonInputString = objectMapper.writeValueAsString(SlettIdenterRequest(identer.toList()))
        deleteRequest("/api/ident", jsonInputString, callId)
    }

    private fun postRequest(action: String, jsonInputString: String, callId: String): Result<HttpResponse<String>> =
        request("POST", action, jsonInputString, callId)

    private fun deleteRequest(action: String, jsonInputString: String, callId: String): Result<HttpResponse<String>> =
        request("DELETE", action, jsonInputString, callId)

    private fun request(method: String, action: String, jsonInputString: String, callId: String): Result<HttpResponse<String>> {
        return try {
            when (val token = tokenProvider.bearerToken(scope)) {
                is AzureTokenProvider.AzureTokenResult.Error -> Result.Error("Feil ved henting av token: ${token.error}", token.exception)
                is AzureTokenProvider.AzureTokenResult.Ok -> {
                    val request = HttpRequest.newBuilder()
                        .uri(URI("$baseUrl$action"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer ${token.azureToken.token}")
                        .header("callId", callId)
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonInputString))
                        .build()

                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() != 200) {
                        convertResponseBody<IdentFeilresponse>(response).map {
                            Result.Error("Feil fra Speed: ${it.detail}")
                        }
                    } else {
                        Result.Ok(response)
                    }
                }
            }
        } catch (err: Exception) {
            Result.Error("Feil ved sending av request: ${err.message}", err)
        }
    }

    private inline fun <reified T> convertResponseBody(response: HttpResponse<String>): Result<T> {
        return try {
            objectMapper.readValue<T>(response.body()).ok()
        } catch (err: Exception) {
            err.error(err.message ?: "JSON parsing error")
        }
    }
}

data class IdentRequest(val ident: String)
data class SlettIdenterRequest(val identer: List<String>)
@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentFeilresponse(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: URI,
    val callId: String?,
    val stacktrace: String? = null
)
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
@JsonIgnoreProperties(ignoreUnknown = true)
data class HistoriskeIdenterResponse(
    val fødselsnumre: List<String>
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonResponse(
    val fødselsdato: LocalDate,
    val dødsdato: LocalDate?,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val adressebeskyttelse: Adressebeskyttelse,
    val kjønn: Kjønn,
) {
    enum class Adressebeskyttelse {
        FORTROLIG, STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND, UGRADERT
    }
    enum class Kjønn {
        MANN, KVINNE, UKJENT
    }
}