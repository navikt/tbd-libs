package com.github.navikt.tbd_libs.spenn

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

class SimuleringClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper,
    private val tokenProvider: AzureTokenProvider,
    baseUrl: String? = null,
    scope: String? = null
) {
    private val baseUrl = baseUrl ?: "http://spenn-simulering-api"
    private val scope = scope ?: "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spenn-simulering-api/.default"

    fun hentSimulering(simulering: SimuleringRequest, callId: String = UUID.randomUUID().toString()): SimuleringResponse {
        val jsonInputString = objectMapper.writeValueAsString(simulering)
        val response = request("/api/simulering", jsonInputString, callId)
        return convertResponseBody(response)
    }

    private fun request(action: String, jsonInputString: String, callId: String): HttpResponse<String> {
        val token = tokenProvider.bearerToken(scope)
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl$action"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.token}")
            .header("callId", callId)
            .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (val status = response.statusCode()) {
            200 -> response
            400 -> {
                val feilmelding = convertResponseBody<SimuleringFeilresponse>(response)
                throw SimuleringFunksjonellFeilException("Feil fra Spenn Simulering: ${feilmelding.feilmelding}")
            }
            503 -> throw SimuleringUtilgjengeligException()
            else -> {
                val feilmelding = convertResponseBody<SimuleringFeilresponse>(response)
                throw SimuleringException("Feil fra Spenn Simulering (http $status): ${feilmelding.feilmelding}")
            }
        }
    }

    private inline fun <reified T> convertResponseBody(response: HttpResponse<String>): T {
        return try {
            objectMapper.readValue<T>(response.body())
        } catch (err: Exception) {
            throw SimuleringException(err.message ?: "JSON parsing error", err)
        }
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
data class SimuleringFeilresponse(val feilmelding: String)
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
class SimuleringUtilgjengeligException() : RuntimeException("Simuleringtjenesten er ikke tilgjengelig")
class SimuleringFunksjonellFeilException(override val message: String, override val cause: Throwable? = null) : RuntimeException()
class SimuleringException(override val message: String, override val cause: Throwable? = null) : RuntimeException()

