package com.github.navikt.tbd_libs.spenn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

class SimuleringClientTest {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Test
    fun `hent simulering`() {
        val simulering = simuleringRequest()
        utveksle(simulering) { body ->
            body.hasNonNull("fødselsnummer") && body.hasNonNull("oppdrag") && body.hasNonNull("maksdato") && body.hasNonNull("saksbehandler")
        }
    }

    @Test
    fun `hent simulering - feil`() {
        val (simuleringClient, httpClient) = mockClient(errorResponse, 404)
        val result = simuleringClient.hentSimulering(simuleringRequest())
        result as SimuleringClient.SimuleringResult.Feilmelding
        assertEquals("Feil fra Spenn Simulering (http 404): noe gikk galt", result.feilmelding)
        assertNull(result.exception)
        verifiserPOST(httpClient)
    }

    @Test
    fun `hent simulering - funksjonell feil`() {
        val (simuleringClient, httpClient) = mockClient(errorResponse, 400)
        val result = simuleringClient.hentSimulering(simuleringRequest())
        result as SimuleringClient.SimuleringResult.FunksjonellFeil
        assertEquals("Feil i requesten vår til Spenn Simulering: noe gikk galt", result.feilmelding)
        verifiserPOST(httpClient)
    }

    @Test
    fun `hent simulering - utilgjengelig tjeneste`() {
        val (simuleringClient, httpClient) = mockClient(errorResponse, 503)
        val result = simuleringClient.hentSimulering(simuleringRequest())
        assertTrue(result is SimuleringClient.SimuleringResult.SimuleringtjenesteUtilgjengelig)
        verifiserPOST(httpClient)
    }

    private fun simuleringRequest() =
        SimuleringRequest(
            fødselsnummer = "fnr",
            maksdato = LocalDate.of(2018, 12, 28),
            saksbehandler = "SPENN",
            oppdrag = SimuleringRequest.Oppdrag(
                fagområde = SimuleringRequest.Oppdrag.Fagområde.ARBEIDSGIVERREFUSJON,
                fagsystemId = "AQOG5K72HRHPPMNULZKJIOZ5GU",
                endringskode = SimuleringRequest.Oppdrag.Endringskode.ENDRET,
                mottakerAvUtbetalingen = "orgnr",
                linjer = listOf(
                    SimuleringRequest.Oppdrag.Oppdragslinje(
                        endringskode = SimuleringRequest.Oppdrag.Endringskode.ENDRET,
                        fom = LocalDate.of(2018, 1, 1),
                        tom = LocalDate.of(2018, 1, 31),
                        satstype = SimuleringRequest.Oppdrag.Oppdragslinje.Satstype.DAGLIG,
                        sats = 2383,
                        grad = 100,
                        delytelseId = 1,
                        refDelytelseId = null,
                        refFagsystemId = null,
                        klassekode = SimuleringRequest.Oppdrag.Oppdragslinje.Klassekode.REFUSJON_IKKE_OPPLYSNINGSPLIKTIG,
                        opphørerFom = null
                    ),
                    SimuleringRequest.Oppdrag.Oppdragslinje(
                        endringskode = SimuleringRequest.Oppdrag.Endringskode.NY,
                        fom = LocalDate.of(2018, 2, 1),
                        tom = LocalDate.of(2018, 2, 28),
                        satstype = SimuleringRequest.Oppdrag.Oppdragslinje.Satstype.DAGLIG,
                        sats = 1191,
                        grad = 50,
                        delytelseId = 2,
                        refDelytelseId = 1,
                        refFagsystemId = "AQOG5K72HRHPPMNULZKJIOZ5GU",
                        klassekode = SimuleringRequest.Oppdrag.Oppdragslinje.Klassekode.REFUSJON_IKKE_OPPLYSNINGSPLIKTIG,
                        opphørerFom = null
                    )
                )
            )
        )

    private fun utveksle(request: SimuleringRequest, verifisering: (body: JsonNode) -> Boolean) {
        val (simuleringClient, httpClient) = mockClient(okResponse)

        val response = simuleringClient.hentSimulering(request)
        response as SimuleringClient.SimuleringResult.Ok
        verifiserPOST(httpClient)
        verifiserRequestBody(httpClient, verifisering)
        assertEquals(SimuleringResponse(
            gjelderId = "02889298149",
            gjelderNavn = "MUFFINS NORMAL",
            datoBeregnet = LocalDate.of(2024, 11, 1),
            totalBelop = -3576,
            periodeList = listOf(
                SimuleringResponse.SimulertPeriode(
                    fom = LocalDate.of(2024, 8, 1),
                    tom = LocalDate.of(2024, 8, 30),
                    utbetaling = listOf(
                        SimuleringResponse.Utbetaling(
                            fagSystemId = "AQOG5K72HRHPPMNULZKJIOZ5GU",
                            utbetalesTilId = "963743254",
                            utbetalesTilNavn = "BESK KAFFE",
                            forfall = LocalDate.of(2024, 11, 1),
                            feilkonto = false,
                            detaljer = listOf(
                                SimuleringResponse.Detaljer(
                                    faktiskFom = LocalDate.of(2024, 8, 1),
                                    faktiskTom = LocalDate.of(2024, 8, 30),
                                    konto = "2338020",
                                    belop = -52426,
                                    tilbakeforing = true,
                                    sats = 2383.0,
                                    typeSats = "DAG",
                                    antallSats = 0,
                                    uforegrad = 100,
                                    klassekode = "SPREFAG-IOP",
                                    klassekodeBeskrivelse = "Sykepenger, Refusjon arbeidsgiver",
                                    utbetalingsType = "YTEL",
                                    refunderesOrgNr = "805824352"
                                )
                            )
                        )
                    )
                ),
                SimuleringResponse.SimulertPeriode(
                    fom = LocalDate.of(2024, 8, 1),
                    tom = LocalDate.of(2024, 8, 27),
                    utbetaling = listOf(
                        SimuleringResponse.Utbetaling(
                            fagSystemId = "AQOG5K72HRHPPMNULZKJIOZ5GU",
                            utbetalesTilId = "963743254",
                            utbetalesTilNavn = "BESK KAFFE",
                            forfall = LocalDate.of(2024, 11, 1),
                            feilkonto = false,
                            detaljer = listOf(
                                SimuleringResponse.Detaljer(
                                    faktiskFom = LocalDate.of(2024, 8, 1),
                                    faktiskTom = LocalDate.of(2024, 8, 27),
                                    konto = "2338020",
                                    belop = 45277,
                                    tilbakeforing = false,
                                    sats = 2383.0,
                                    typeSats = "DAG",
                                    antallSats = 19,
                                    uforegrad = 100,
                                    klassekode = "SPREFAG-IOP",
                                    klassekodeBeskrivelse = "Sykepenger, Refusjon arbeidsgiver",
                                    utbetalingsType = "YTEL",
                                    refunderesOrgNr = "805824352"
                                )
                            )
                        )
                    )
                ),
                SimuleringResponse.SimulertPeriode(
                    fom = LocalDate.of(2024, 8, 28),
                    tom = LocalDate.of(2024, 8, 30),
                    utbetaling = listOf(
                        SimuleringResponse.Utbetaling(
                            fagSystemId = "AQOG5K72HRHPPMNULZKJIOZ5GU",
                            utbetalesTilId = "963743254",
                            utbetalesTilNavn = "BESK KAFFE",
                            forfall = LocalDate.of(2024, 11, 1),
                            feilkonto = false,
                            detaljer = listOf(
                                SimuleringResponse.Detaljer(
                                    faktiskFom = LocalDate.of(2024, 8, 28),
                                    faktiskTom = LocalDate.of(2024, 8, 30),
                                    konto = "2338020",
                                    belop = 3573,
                                    tilbakeforing = false,
                                    sats = 1191.0,
                                    typeSats = "DAG",
                                    antallSats = 3,
                                    uforegrad = 50,
                                    klassekode = "SPREFAG-IOP",
                                    klassekodeBeskrivelse = "Sykepenger, Refusjon arbeidsgiver",
                                    utbetalingsType = "YTEL",
                                    refunderesOrgNr = "805824352"
                                )
                            )
                        )
                    )
                )
            )
        ), response.data)
    }

    private fun mockClient(response: String, statusCode: Int = 200): Pair<SimuleringClient, HttpClient> {
        val httpClient = mockk<HttpClient> {
            every {
                send<String>(any(), any())
            } returns MockHttpResponse(response, statusCode)
        }
        val tokenProvider = object : AzureTokenProvider {
            override fun onBehalfOfToken(scope: String, token: String): AzureTokenProvider.AzureTokenResult {
                return AzureTokenProvider.AzureTokenResult.Ok(AzureToken("on_behalf_of_token", LocalDateTime.now()))
            }

            override fun bearerToken(scope: String): AzureTokenProvider.AzureTokenResult {
                return AzureTokenProvider.AzureTokenResult.Ok(AzureToken("bearer_token", LocalDateTime.now()))
            }
        }
        val simuleringClient = SimuleringClient(httpClient, objectMapper, tokenProvider)
        return simuleringClient to httpClient
    }

    fun verifiserPOST(httpClient: HttpClient) {
        verifiserRequestMethod(httpClient, "POST")
    }

    fun verifiserRequestMethod(httpClient: HttpClient, method: String) {
        verify {
            httpClient.send<String>(match { request ->
                request.method().uppercase() == method.uppercase()
            }, any())
        }
    }

    fun verifiserRequestHeader(httpClient: HttpClient, headerName: String, verifisering: (String?) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(request.headers().firstValue(headerName).getOrNull())
            }, any())
        }
    }

    private fun verifiserRequestBody(httpClient: HttpClient, verifisering: (body: JsonNode) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(objectMapper.readTree(request.bodyAsString()))
            }, any())
        }
    }

    @Language("JSON")
    private val errorResponse = """{
  "type": "urn:error:internal_error",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "noe gikk galt",
  "instance": "/problem",
  "callId": "${UUID.randomUUID()}",
  "stacktrace": null
}"""

    @Language("JSON")
    private val okResponse = """{
    "gjelderId": "02889298149",
    "gjelderNavn": "MUFFINS NORMAL",
    "datoBeregnet": "2024-11-01",
    "totalBelop": -3576,
    "periodeList": [
        {
            "fom": "2024-08-01",
            "tom": "2024-08-30",
            "utbetaling": [
                {
                    "fagSystemId": "AQOG5K72HRHPPMNULZKJIOZ5GU",
                    "utbetalesTilId": "963743254",
                    "utbetalesTilNavn": "BESK KAFFE",
                    "forfall": "2024-11-01",
                    "feilkonto": false,
                    "detaljer": [
                        {
                            "faktiskFom": "2024-08-01",
                            "faktiskTom": "2024-08-30",
                            "konto": "2338020",
                            "belop": -52426,
                            "tilbakeforing": true,
                            "sats": 2383.0,
                            "typeSats": "DAG",
                            "antallSats": 0,
                            "uforegrad": 100,
                            "klassekode": "SPREFAG-IOP",
                            "klassekodeBeskrivelse": "Sykepenger, Refusjon arbeidsgiver",
                            "utbetalingsType": "YTEL",
                            "refunderesOrgNr": "805824352"
                        }
                    ]
                }
            ]
        },
        {
            "fom": "2024-08-01",
            "tom": "2024-08-27",
            "utbetaling": [
                {
                    "fagSystemId": "AQOG5K72HRHPPMNULZKJIOZ5GU",
                    "utbetalesTilId": "963743254",
                    "utbetalesTilNavn": "BESK KAFFE",
                    "forfall": "2024-11-01",
                    "feilkonto": false,
                    "detaljer": [
                        {
                            "faktiskFom": "2024-08-01",
                            "faktiskTom": "2024-08-27",
                            "konto": "2338020",
                            "belop": 45277,
                            "tilbakeforing": false,
                            "sats": 2383.0,
                            "typeSats": "DAG",
                            "antallSats": 19,
                            "uforegrad": 100,
                            "klassekode": "SPREFAG-IOP",
                            "klassekodeBeskrivelse": "Sykepenger, Refusjon arbeidsgiver",
                            "utbetalingsType": "YTEL",
                            "refunderesOrgNr": "805824352"
                        }
                    ]
                }
            ]
        },
        {
            "fom": "2024-08-28",
            "tom": "2024-08-30",
            "utbetaling": [
                {
                    "fagSystemId": "AQOG5K72HRHPPMNULZKJIOZ5GU",
                    "utbetalesTilId": "963743254",
                    "utbetalesTilNavn": "BESK KAFFE",
                    "forfall": "2024-11-01",
                    "feilkonto": false,
                    "detaljer": [
                        {
                            "faktiskFom": "2024-08-28",
                            "faktiskTom": "2024-08-30",
                            "konto": "2338020",
                            "belop": 3573,
                            "tilbakeforing": false,
                            "sats": 1191.0,
                            "typeSats": "DAG",
                            "antallSats": 3,
                            "uforegrad": 50,
                            "klassekode": "SPREFAG-IOP",
                            "klassekodeBeskrivelse": "Sykepenger, Refusjon arbeidsgiver",
                            "utbetalingsType": "YTEL",
                            "refunderesOrgNr": "805824352"
                        }
                    ]
                }
            ]
        }
    ]
}"""
}