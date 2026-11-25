package com.github.navikt.tbd_libs.populasjonstilgang.client

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import kotlin.jvm.optionals.getOrNull

class TilgangsmaskinenClientTest {
    @TestFactory
    fun `prøver alle tilgangssjekker på nytt ved tilkoblingsfeil`() =
        listOf(
            "komplett" to TilgangsmaskinenClient::kontrollerKomplettTilgang,
            "kjerne" to TilgangsmaskinenClient::kontrollerKjerneTilgang,
            "kjerne for ansatt" to TilgangsmaskinenClient::kontrollerKjerneTilgangForAnsatt,
        ).map { (navn, kontrollerTilgang) ->
            dynamicTest(navn) {
                val (client, httpClient) = mockClient("", 204)
                every { httpClient.send<String>(any(), any()) } throws ConnectException() andThen MockHttpResponse("", 204)

                assertEquals(TilgangskontrollResultat.Ok, kontrollerTilgang(client, "ident", "12345678901"))
                verify(exactly = 2) { httpClient.send<String>(any(), any()) }
            }
        }

    @Test
    fun `gir opp etter tre ekstra forsøk ved vedvarende tilkoblingsfeil`() {
        val (client, httpClient) = mockClient("", 204)
        val feil = ConnectException()
        every { httpClient.send<String>(any(), any()) } throws feil

        assertSame(
            feil,
            assertThrows<ConnectException> { client.kontrollerKjerneTilgang("access_token", "12345678901") },
        )
        verify(exactly = 4) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `prøver på nytt når serveren lukker en gjenbrukt tilkobling`() {
        val (client, httpClient) = mockClient("", 204)
        every { httpClient.send<String>(any(), any()) } throws
            IOException("HTTP/1.1 header parser received no bytes") andThen MockHttpResponse("", 204)

        assertEquals(TilgangskontrollResultat.Ok, client.kontrollerKjerneTilgang("access_token", "12345678901"))
        verify(exactly = 2) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `prøver ikke på nytt ved tidsavbrudd`() {
        val (client, httpClient) = mockClient("", 204)
        every { httpClient.send<String>(any(), any()) } throws HttpTimeoutException("request timed out")

        assertThrows<HttpTimeoutException> { client.kontrollerKjerneTilgang("access_token", "12345678901") }
        verify(exactly = 1) { httpClient.send<String>(any(), any()) }
    }

    @Test
    fun `prøver ikke på nytt når tråden blir avbrutt`() {
        val (client, httpClient) = mockClient("", 204)
        every { httpClient.send<String>(any(), any()) } throws InterruptedException()

        assertThrows<InterruptedException> { client.kontrollerKjerneTilgang("access_token", "12345678901") }
        verify(exactly = 1) { httpClient.send<String>(any(), any()) }
    }

    @TestFactory
    fun `prøver ikke på nytt ved HTTP-feilsvar`() =
        listOf(403, 404, 500).map { statusCode ->
            dynamicTest("$statusCode") {
                val (client, httpClient) = mockClient(avvistResponse("AVVIST_SKJERMING"), statusCode)

                client.kontrollerKjerneTilgang("access_token", "12345678901")

                verify(exactly = 1) { httpClient.send<String>(any(), any()) }
            }
        }

    @Test
    fun `tilgang ok - 204`() {
        val (client, httpClient) = mockClient("", 204)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.Ok, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `ident ikke funnet - 404`() {
        val (client, httpClient) = mockClient("", 404)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.IdentIkkeFunnet, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `mangler tilgang - strengt fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresse),
            result,
        )
    }

    @Test
    fun `mangler tilgang - strengt fortrolig adresse utland`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_UTLAND"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresseUtland),
            result,
        )
    }

    @Test
    fun `mangler tilgang - fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse), result)
    }

    @Test
    fun `mangler tilgang - skjerming (egen ansatt)`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_SKJERMING"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt), result)
    }

    @Test
    fun `mangler tilgang - habilitet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_HABILITET"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet), result)
    }

    @Test
    fun `mangler tilgang - verge`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_VERGE"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Verge), result)
    }

    @Test
    fun `mangler tilgang - geografisk tilhørighet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_GEOGRAFISK"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.GeografiskTilhørighet),
            result,
        )
    }

    @Test
    fun `mangler tilgang - person død`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_AVDØD"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.PersonDød), result)
    }

    @Test
    fun `mangler tilgang - ukjent tilgangSomMangler`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_UKJENT_GRUNN"), 403)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("AVVIST_UKJENT_GRUNN"))
    }

    @Test
    fun `uventet statuskode`() {
        val (client, _) = mockClient("", 500)
        val result = client.kontrollerKomplettTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("500"))
    }

    @Test
    fun `sender fødselsnummer i request body`() {
        val fødselsnummer = "12345678901"
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKomplettTilgang("access_token", fødselsnummer)
        verify {
            httpClient.send<String>(
                match { request ->
                    request.bodyAsString() == fødselsnummer
                },
                any(),
            )
        }
    }

    @Test
    fun `sender obo-token i authorization header`() {
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKomplettTilgang("access_token", "12345678901")
        verifiserRequestHeader(httpClient, "Authorization") { it == "Bearer on_behalf_of_token" }
    }

    @Test
    fun `kjerne - tilgang ok - 204`() {
        val (client, httpClient) = mockClient("", 204)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.Ok, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `kjerne - ident ikke funnet - 404`() {
        val (client, httpClient) = mockClient("", 404)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.IdentIkkeFunnet, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `kjerne - mangler tilgang - strengt fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresse),
            result,
        )
    }

    @Test
    fun `kjerne - mangler tilgang - strengt fortrolig adresse utland`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_UTLAND"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresseUtland),
            result,
        )
    }

    @Test
    fun `kjerne - mangler tilgang - fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse), result)
    }

    @Test
    fun `kjerne - mangler tilgang - skjerming (egen ansatt)`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_SKJERMING"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt), result)
    }

    @Test
    fun `kjerne - mangler tilgang - habilitet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_HABILITET"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet), result)
    }

    @Test
    fun `kjerne - mangler tilgang - verge`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_VERGE"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Verge), result)
    }

    @Test
    fun `kjerne - mangler tilgang - geografisk tilhørighet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_GEOGRAFISK"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.GeografiskTilhørighet),
            result,
        )
    }

    @Test
    fun `kjerne - mangler tilgang - person død`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_AVDØD"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.PersonDød), result)
    }

    @Test
    fun `kjerne - mangler tilgang - ukjent tilgangSomMangler`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_UKJENT_GRUNN"), 403)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("AVVIST_UKJENT_GRUNN"))
    }

    @Test
    fun `kjerne - uventet statuskode`() {
        val (client, _) = mockClient("", 500)
        val result = client.kontrollerKjerneTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("500"))
    }

    @Test
    fun `kjerne - sender fødselsnummer i request body`() {
        val fødselsnummer = "12345678901"
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKjerneTilgang("access_token", fødselsnummer)
        verify {
            httpClient.send<String>(
                match { request ->
                    request.bodyAsString() == fødselsnummer
                },
                any(),
            )
        }
    }

    @Test
    fun `kjerne - sender obo-token i authorization header`() {
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKjerneTilgang("access_token", "12345678901")
        verifiserRequestHeader(httpClient, "Authorization") { it == "Bearer on_behalf_of_token" }
    }

    @Test
    fun `kjerne for ansattId - tilgang ok - 204`() {
        val (client, httpClient) = mockClient("", 204)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.Ok, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `kjerne for ansattId - ident ikke funnet - 404`() {
        val (client, httpClient) = mockClient("", 404)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.IdentIkkeFunnet, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - strengt fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresse),
            result,
        )
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - strengt fortrolig adresse utland`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_UTLAND"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresseUtland),
            result,
        )
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse), result)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - skjerming (egen ansatt)`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_SKJERMING"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt), result)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - habilitet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_HABILITET"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet), result)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - verge`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_VERGE"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Verge), result)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - geografisk tilhørighet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_GEOGRAFISK"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.GeografiskTilhørighet),
            result,
        )
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - person død`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_AVDØD"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.PersonDød), result)
    }

    @Test
    fun `kjerne for ansattId - mangler tilgang - ukjent tilgangSomMangler`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_UKJENT_GRUNN"), 403)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("AVVIST_UKJENT_GRUNN"))
    }

    @Test
    fun `kjerne for ansattId - uventet statuskode`() {
        val (client, _) = mockClient("", 500)
        val result = client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("500"))
    }

    @Test
    fun `kjerne for ansattId - sender fødselsnummer i request body`() {
        val fødselsnummer = "12345678901"
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKjerneTilgangForAnsatt("Z999999", fødselsnummer)
        verify {
            httpClient.send<String>(
                match { request ->
                    request.bodyAsString() == fødselsnummer
                },
                any(),
            )
        }
    }

    @Test
    fun `kjerne for ansattId - sender ansattId i URL-path`() {
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        verify {
            httpClient.send<String>(
                match { request ->
                    request.uri().path.endsWith("/api/v1/ccf/kjerne/Z999999")
                },
                any(),
            )
        }
    }

    @Test
    fun `kjerne for ansattId - sender maskin-token i authorization header`() {
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerKjerneTilgangForAnsatt("Z999999", "12345678901")
        verifiserRequestHeader(httpClient, "Authorization") { it == "Bearer machine_token" }
    }

    private fun verifiserPOST(httpClient: HttpClient) {
        verify {
            httpClient.send<String>(
                match { request ->
                    request.method().uppercase() == "POST"
                },
                any(),
            )
        }
    }

    private fun verifiserRequestHeader(
        httpClient: HttpClient,
        headerName: String,
        verifisering: (String?) -> Boolean,
    ) {
        verify {
            httpClient.send<String>(
                match { request ->
                    verifisering(request.headers().firstValue(headerName).getOrNull())
                },
                any(),
            )
        }
    }

    private fun mockClient(
        response: String,
        statusCode: Int = 200,
    ): Pair<TilgangsmaskinenClient, HttpClient> {
        val httpClient =
            mockk<HttpClient> {
                every {
                    send<String>(any(), any())
                } returns MockHttpResponse(response, statusCode)
            }
        val tokenProvider =
            object : AccessTokenProvider {
                override fun machineToken(scope: String): String = "machine_token"

                override fun oboToken(
                    accessToken: String,
                    scope: String,
                ): String = "on_behalf_of_token"
            }
        val tilgangsmaskinenClient =
            TilgangsmaskinenClient(
                scope = "test_scope",
                baseUrl = "http://test-url",
                tokenProvider = tokenProvider,
                httpClient = httpClient,
            )
        return tilgangsmaskinenClient to httpClient
    }

    @Language("JSON")
    private fun avvistResponse(title: String) =
        """{
        "title": "$title"
    }"""
}
