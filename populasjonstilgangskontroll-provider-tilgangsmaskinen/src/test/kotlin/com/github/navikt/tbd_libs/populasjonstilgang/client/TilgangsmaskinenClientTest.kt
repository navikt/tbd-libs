package com.github.navikt.tbd_libs.populasjonstilgang.client

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.http.HttpClient
import kotlin.jvm.optionals.getOrNull
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TilgangsmaskinenClientTest {

    @Test
    fun `tilgang ok - 204`() {
        val (client, httpClient) = mockClient("", 204)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.Ok, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `ident ikke funnet - 404`() {
        val (client, httpClient) = mockClient("", 404)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.IdentIkkeFunnet, result)
        verifiserPOST(httpClient)
    }

    @Test
    fun `mangler tilgang - strengt fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresse),
            result
        )
    }

    @Test
    fun `mangler tilgang - strengt fortrolig adresse utland`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_STRENGT_FORTROLIG_UTLAND"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresseUtland),
            result
        )
    }

    @Test
    fun `mangler tilgang - fortrolig adresse`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_FORTROLIG_ADRESSE"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse), result)
    }

    @Test
    fun `mangler tilgang - skjerming (egen ansatt)`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_SKJERMING"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt), result)
    }

    @Test
    fun `mangler tilgang - habilitet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_HABILITET"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet), result)
    }

    @Test
    fun `mangler tilgang - verge`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_VERGE"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Verge), result)
    }

    @Test
    fun `mangler tilgang - geografisk tilhørighet`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_GEOGRAFISK"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(
            TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.GeografiskTilhørighet),
            result
        )
    }

    @Test
    fun `mangler tilgang - person død`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_AVDØD"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        assertEquals(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.PersonDød), result)
    }

    @Test
    fun `mangler tilgang - ukjent tilgangSomMangler`() {
        val (client, _) = mockClient(avvistResponse("AVVIST_UKJENT_GRUNN"), 403)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("AVVIST_UKJENT_GRUNN"))
    }

    @Test
    fun `uventet statuskode`() {
        val (client, _) = mockClient("", 500)
        val result = client.kontrollerTilgang("access_token", "12345678901")
        result as TilgangskontrollResultat.UventetFeil
        Assertions.assertTrue(result.menneskeligLesbarForklaring.contains("500"))
    }

    @Test
    fun `sender fødselsnummer i request body`() {
        val fødselsnummer = "12345678901"
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerTilgang("access_token", fødselsnummer)
        verify {
            httpClient.send<String>(match { request ->
                request.bodyAsString() == fødselsnummer
            }, any())
        }
    }

    @Test
    fun `sender obo-token i authorization header`() {
        val (client, httpClient) = mockClient("", 204)
        client.kontrollerTilgang("access_token", "12345678901")
        verifiserRequestHeader(httpClient, "Authorization") { it == "Bearer on_behalf_of_token" }
    }

    private fun verifiserPOST(httpClient: HttpClient) {
        verify {
            httpClient.send<String>(match { request ->
                request.method().uppercase() == "POST"
            }, any())
        }
    }

    private fun verifiserRequestHeader(httpClient: HttpClient, headerName: String, verifisering: (String?) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(request.headers().firstValue(headerName).getOrNull())
            }, any())
        }
    }

    private fun mockClient(response: String, statusCode: Int = 200): Pair<TilgangsmaskinenClient, HttpClient> {
        val httpClient = mockk<HttpClient> {
            every {
                send<String>(any(), any())
            } returns MockHttpResponse(response, statusCode)
        }
        val tokenProvider = object : AccessTokenProvider {
            override fun machineToken(scope: String): String {
                return "machine_token"
            }

            override fun oboToken(accessToken: String, scope: String): String {
                return "on_behalf_of_token"
            }
        }
        val tilgangsmaskinenClient = TilgangsmaskinenClient(
            scope = "test_scope",
            baseUrl = "http://test-url",
            tokenProvider = tokenProvider,
            httpClient = httpClient
        )
        return tilgangsmaskinenClient to httpClient
    }

    @Language("JSON")
    private fun avvistResponse(title: String) = """{
        "title": "$title"
    }"""
}
