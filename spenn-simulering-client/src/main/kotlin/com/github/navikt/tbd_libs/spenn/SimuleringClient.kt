package com.github.navikt.tbd_libs.spenn

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
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

    fun hentSimulering(simulering: SimuleringRequest, callId: String = UUID.randomUUID().toString()): SimuleringResult {
        return try {
            when (val token = tokenProvider.bearerToken(scope)) {
                is AzureTokenProvider.AzureTokenResult.Error -> SimuleringResult.Feilmelding("Feil ved henting av token: ${token.error}", token.exception)
                is AzureTokenProvider.AzureTokenResult.Ok -> {
                    val jsonInputString = objectMapper.writeValueAsString(simulering)
                    val response = request("/api/simulering", token.azureToken, jsonInputString, callId)
                    håndterRespons(response)
                }
            }
        } catch (err: Exception) {
            SimuleringResult.Feilmelding("Feil ved simulering: ${err.message}", err)
        }
    }

    private fun håndterRespons(response: HttpResponse<String>): SimuleringResult {
        val body: String? = response.body()
        if (body == null) return SimuleringResult.Feilmelding("Tom responskropp fra simulering-api")
        return tolkResponskoder(response.statusCode(), body)
    }

    private fun tolkResponskoder(status: Int, body: String): SimuleringResult {
        return when (status) {
            200 -> convertResponseBody<SimuleringResponse>(body).fold(
                onSuccess = { SimuleringResult.Ok(it) },
                onFailure = { SimuleringResult.Feilmelding(it.message ?: "JSON parsing error", it) }
            )
            204 -> SimuleringResult.OkMenTomt
            400 -> {
                convertResponseBody<SimuleringFeilresponse>(body).fold(
                    onSuccess = { SimuleringResult.FunksjonellFeil("Feil i requesten vår til Spenn Simulering: ${it.detail}") },
                    onFailure = { SimuleringResult.Feilmelding("Det er feil i requesten vår, men vi klarte ikke tolke feilrespons fra Spenn simulering: ${it.message}", it) }
                )
            }
            503 -> SimuleringResult.SimuleringtjenesteUtilgjengelig
            else -> {
                convertResponseBody<SimuleringFeilresponse>(body).fold(
                    onSuccess = { SimuleringResult.Feilmelding("Feil fra Spenn Simulering (http $status): ${it.detail}") },
                    onFailure = { SimuleringResult.Feilmelding("Klarte ikke tolke feilrespons fra Spenn simulering (http $status): ${it.message}", it) }
                )
            }
        }
    }

    private fun request(action: String, bearerToken: AzureToken, jsonInputString: String, callId: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl$action"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${bearerToken.token}")
            .header("callId", callId)
            .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private inline fun <reified T> convertResponseBody(response: String): Result<T> {
        return try {
            Result.success(objectMapper.readValue<T>(response))
        } catch (err: Exception) {
            Result.failure(err)
        }
    }

    sealed interface SimuleringResult {
        data class Ok(val data: SimuleringResponse) : SimuleringResult
        data object OkMenTomt : SimuleringResult
        data object SimuleringtjenesteUtilgjengelig : SimuleringResult
        data class FunksjonellFeil(val feilmelding: String) : SimuleringResult
        data class Feilmelding(val feilmelding: String, val exception: Throwable? = null) : SimuleringResult
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
                SYKEPENGER_ARBEIDSTAKER_FERIEPENGER
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
