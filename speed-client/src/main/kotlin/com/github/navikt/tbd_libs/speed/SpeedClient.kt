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

    fun hentFødselsnummerOgAktørId(ident: String, callId: String = UUID.randomUUID().toString()): IdentResponse {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        val response = postRequest("/api/ident", jsonInputString, callId)
        return convertResponseBody(response)
    }

    fun hentPersoninfo(ident: String, callId: String = UUID.randomUUID().toString()): PersonResponse {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        val response = postRequest("/api/person", jsonInputString, callId)
        return convertResponseBody(response)
    }

    fun hentHistoriskeFødselsnumre(ident: String, callId: String = UUID.randomUUID().toString()): HistoriskeIdenterResponse {
        val jsonInputString = objectMapper.writeValueAsString(IdentRequest(ident))
        val response = postRequest("/api/identer", jsonInputString, callId)
        return convertResponseBody(response)
    }

    fun tømMellomlager(identer: Collection<String>, callId: String = UUID.randomUUID().toString()) {
        val jsonInputString = objectMapper.writeValueAsString(SlettIdenterRequest(identer.toList()))
        deleteRequest("/api/ident", jsonInputString, callId)
    }

    private fun postRequest(action: String, jsonInputString: String, callId: String): HttpResponse<String> =
        request("POST", action, jsonInputString, callId)

    private fun deleteRequest(action: String, jsonInputString: String, callId: String): HttpResponse<String> =
        request("DELETE", action, jsonInputString, callId)

    private fun request(method: String, action: String, jsonInputString: String, callId: String): HttpResponse<String> {
        val token = tokenProvider.bearerToken(scope)
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl$action"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.token}")
            .header("callId", callId)
            .method(method, HttpRequest.BodyPublishers.ofString(jsonInputString))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            val feilmelding = convertResponseBody<IdentFeilresponse>(response)
            throw SpeedException("Feil fra Speed: ${feilmelding.feilmelding}")
        }
        return response
    }

    private inline fun <reified T> convertResponseBody(response: HttpResponse<String>): T {
        return try {
            objectMapper.readValue<T>(response.body())
        } catch (err: Exception) {
            throw SpeedException(err.message ?: "JSON parsing error", err)
        }
    }
}

data class IdentRequest(val ident: String)
data class SlettIdenterRequest(val identer: List<String>)
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
class SpeedException(override val message: String, override val cause: Throwable? = null) : RuntimeException()

