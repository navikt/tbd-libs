package no.nav.helse.speil.backend.app.testfixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

/**
 * Mocker tilgangsmaskinen sine endepunkter (`/api/v1/komplett`, `/api/v1/kjerne`) slik at
 * [com.github.navikt.tbd_libs.populasjonstilgang.client.TilgangsmaskinenClient] kan brukes i tester
 * uten et ekte kall til tilgangsmaskin-tjenesten.
 */
class MockTilgangsmaskinenServer private constructor(
    private val wireMockServer: WireMockServer,
) {
    val baseUrl: String get() = wireMockServer.baseUrl()

    fun stubInnvilgetTilgang() {
        stubFor(post(urlEqualTo("/api/v1/komplett")).willReturn(aResponse().withStatus(204)))
        stubFor(post(urlEqualTo("/api/v1/kjerne")).willReturn(aResponse().withStatus(204)))
    }

    fun stubAvvistTilgang(tittel: String = "AVVIST_HABILITET") {
        val body = """{"title":"$tittel"}"""
        stubFor(post(urlEqualTo("/api/v1/komplett")).willReturn(aResponse().withStatus(403).withBody(body)))
        stubFor(post(urlEqualTo("/api/v1/kjerne")).willReturn(aResponse().withStatus(403).withBody(body)))
    }

    fun stubPersonIkkeFunnet() {
        stubFor(post(urlEqualTo("/api/v1/komplett")).willReturn(aResponse().withStatus(404)))
        stubFor(post(urlEqualTo("/api/v1/kjerne")).willReturn(aResponse().withStatus(404)))
    }

    fun stop() = wireMockServer.stop()

    companion object {
        fun start(): MockTilgangsmaskinenServer {
            val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
            server.start()
            return MockTilgangsmaskinenServer(server)
        }
    }
}
