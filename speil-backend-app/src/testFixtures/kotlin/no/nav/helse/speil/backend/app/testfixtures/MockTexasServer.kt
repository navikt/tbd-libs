package no.nav.helse.speil.backend.app.testfixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

/**
 * Mocker Texas sine to endepunkter (`/api/v1/token` og `/api/v1/token/exchange`) slik at
 * [com.github.navikt.tbd_libs.access_token.TexasClient] kan brukes i tester uten en ekte
 * NAIS-sidecar. Bruk `server.baseUrl()/api/v1/token` og `.../token/exchange` som endepunkter.
 */
class MockTexasServer private constructor(
    private val wireMockServer: WireMockServer,
) {
    val baseUrl: String get() = wireMockServer.baseUrl()

    fun stubToken(accessToken: String = "test-m2m-token") {
        stubFor(
            post(urlEqualTo("/api/v1/token")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"$accessToken","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
    }

    fun stubTokenExchange(oboToken: String = "test-obo-token") {
        stubFor(
            post(urlEqualTo("/api/v1/token/exchange")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"access_token":"$oboToken","token_type":"Bearer","expires_in":3600}"""),
            ),
        )
    }

    fun stop() = wireMockServer.stop()

    companion object {
        fun start(): MockTexasServer {
            val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
            server.start()
            return MockTexasServer(server)
        }
    }
}
