package no.nav.helse.speil.backend.app.testfixtures

import io.ktor.server.application.Application
import no.nav.helse.speil.backend.app.openapi.OpenApiConfig
import no.nav.helse.speil.backend.app.openapi.configureOpenApiPlugin
import no.nav.helse.speil.backend.app.plugins.configureCallId
import no.nav.helse.speil.backend.app.plugins.configureCallLogging
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.plugins.configureStatusPages

/**
 * Setter opp de plugin-uavhengige delene av appen (logging, content negotiation, feilhåndtering,
 * resources, OpenAPI) i en `testApplication`-kontekst, uten auth. Brukes som byggekloss i
 * spv/libbens egne tester.
 *
 * NB: `configureJwtAuthentication` og `RestAdapter` er 🔴 rød sone og fortsatt kun stubbet
 * (kaster `TODO()`) — fullstendige ende-til-ende-tester av autentiserte/autoriserte kall kan først
 * skrives (og aktiveres, se `@Disabled`-testene i `auth`/`rest`-pakkene) når de er implementert.
 */
fun Application.installTestPlugins(
    openApiConfig: OpenApiConfig = OpenApiConfig(eksponerOpenApi = true, tittel = "test"),
) {
    configureCallId()
    configureCallLogging()
    configureContentNegotiation()
    configureStatusPages()
    configureResources()
    configureOpenApiPlugin(openApiConfig)
}
