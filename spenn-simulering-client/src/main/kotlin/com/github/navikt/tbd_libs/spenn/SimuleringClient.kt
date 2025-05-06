package com.github.navikt.tbd_libs.spenn

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
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.*

class SimuleringClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    baseUrl: String? = null,
    scope: String? = null
) {
    private val baseUrl = baseUrl ?: "http://spenn-simulering-api"
    private val scope = scope ?: "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spenn-simulering-api/.default"

    fun hentSimulering(simulering: SimuleringRequest, callId: String = UUID.randomUUID().toString()): Result<SimuleringResult> {
        return tokenProvider.bearerToken(scope)
            .map { token ->
                val jsonInputString = objectMapper.writeValueAsString(simulering)
                request("/api/simulering", token, jsonInputString, callId)
            }
            .map { response ->
                håndterRespons(response)
            }
    }

    private fun håndterRespons(response: HttpResponse<String>): Result<SimuleringResult> {
        val body: String? = response.body()
        if (body == null) return "Tom responskropp fra simulering-api".error()
        return tolkResponskoder(response.statusCode(), body)
    }

    private fun tolkResponskoder(status: Int, body: String): Result<SimuleringResult> {
        return when (status) {
            200 -> convertResponseBody<SimuleringResponse>(body).map {
                SimuleringResult.Ok(it).ok()
            }
            204 -> SimuleringResult.OkMenTomt.ok()
            400 -> convertResponseBody<SimuleringFeilresponse>(body).fold(
                whenOk = { SimuleringResult.FunksjonellFeil("Feil i requesten vår til Spenn Simulering: ${it.detail}").ok() },
                whenError = { msg, cause -> "Det er feil i requesten vår, men vi klarte ikke tolke feilrespons fra Spenn simulering: $msg".error(cause) }
            )
            503 -> SimuleringResult.SimuleringtjenesteUtilgjengelig.ok()
            else -> {
                convertResponseBody<SimuleringFeilresponse>(body).fold(
                    whenOk = { "Feil fra Spenn Simulering (http $status): ${it.detail}".error() },
                    whenError = { msg, cause -> "Klarte ikke tolke feilrespons fra Spenn simulering (http $status): $msg".error(cause) }
                )
            }
        }
    }

    private fun request(action: String, bearerToken: AzureToken, jsonInputString: String, callId: String): Result<HttpResponse<String>> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl$action"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${bearerToken.token}")
                .header("callId", callId)
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
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

    sealed interface SimuleringResult {
        data class Ok(val data: SimuleringResponse) : SimuleringResult
        data object OkMenTomt : SimuleringResult
        data object SimuleringtjenesteUtilgjengelig : SimuleringResult
        data class FunksjonellFeil(val feilmelding: String) : SimuleringResult
    }
}

data class SimuleringRequest(
    val fødselsnummer: String,
    val oppdrag: Oppdrag,
    val maksdato: LocalDate?,
    val saksbehandler: String
) {
    data class Oppdrag(
        val fagområde: Fagområde,
        val fagsystemId: String,
        val endringskode: Endringskode,
        val mottakerAvUtbetalingen: String,
        val linjer: List<Oppdragslinje>
    ) {
        enum class Fagområde {
            ARBEIDSGIVERREFUSJON,
            BRUKERUTBETALING
        }
        enum class Endringskode {
            NY, ENDRET, IKKE_ENDRET
        }

        data class Oppdragslinje(
            val endringskode: Endringskode,
            val fom: LocalDate,
            val tom: LocalDate,
            val satstype: Satstype,
            val sats: Int,
            val grad: Int?,

            val delytelseId: Int,
            val refDelytelseId: Int?,
            val refFagsystemId: String?,

            val klassekode: Klassekode,
            val opphørerFom: LocalDate?
        ) {
            enum class Satstype {
                DAGLIG, ENGANGS
            }
            enum class Klassekode {
                REFUSJON_IKKE_OPPLYSNINGSPLIKTIG,
                REFUSJON_FERIEPENGER_IKKE_OPPLYSNINGSPLIKTIG,
                SYKEPENGER_ARBEIDSTAKER_ORDINÆR,
                SYKEPENGER_ARBEIDSTAKER_FERIEPENGER,
                SELVSTENDIG_NÆRINGSDRIVENDE
            }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SimuleringFeilresponse(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: URI,
    val callId: String?,
    val stacktrace: String? = null
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SimuleringResponse(
    val gjelderId: String,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val totalBelop: Int,
    val periodeList: List<SimulertPeriode>
) {
    data class SimulertPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val utbetaling: List<Utbetaling>
    )

    data class Utbetaling(
        val fagSystemId: String,
        val utbetalesTilId: String,
        val utbetalesTilNavn: String,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val detaljer: List<Detaljer>
    )

    data class Detaljer(
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val konto: String,
        val belop: Int,
        val tilbakeforing: Boolean,
        val sats: Double,
        val typeSats: String,
        val antallSats: Int,
        val uforegrad: Int,
        val klassekode: String,
        val klassekodeBeskrivelse: String,
        val utbetalingsType: String,
        val refunderesOrgNr: String
    )
}
