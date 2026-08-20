package no.nav.helse.speil.backend.app.openapi

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.speil.backend.app.serialization.customSerializersModule

/**
 * OpenAPI-spec autogenereres fra ktor `Resources`-rutene via `SchemaGenerator.kotlinx`, akkurat
 * som i (jf. : "sjekk at speccen blir generert på samme måte som i
 * ") — `customSerializersModule` deles med [no.nav.helse.speil.backend.app.plugins.configureResources]
 * slik at runtime-(de)serialisering og dokumentert schema er konsistente (bl.a. for `UUID`).
 *
 * Eksponeres KUN dersom [OpenApiConfig.eksponerOpenApi] er satt (jf. ,
 * 2) — default `false`, altså ingen uautentisert `/api/openapi.json` i prod med
 * mindre det eksplisitt slås på.
 *
 * NB: må installeres FØR rutene den skal dokumentere registreres (se rekkefølgen i `StartApp.kt`),
 * siden auto-dokumentasjonen henger seg på ruteregistreringen.
 */
fun Application.configureOpenApiPlugin(config: OpenApiConfig) {
  if (!config.eksponerOpenApi) {
    loggInfo("OpenAPI/Swagger er ikke eksponert (EKSPONER_OPENAPI=false)")
    return
  }
  install(OpenApi) {
    info {
      title = config.tittel
      version = config.versjon
    }
    pathFilter = { _, url -> url.firstOrNull() == "api" }
    autoDocumentResourcesRoutes = true
    schemas {
      generator =
        SchemaGenerator.kotlinx {
          serializersModule = customSerializersModule
          overwrite(SchemaGenerator.TypeOverwrites.JavaUuid())
        }
    }
  }
  routing {
    route("/api") {
      route("openapi.json") {
        openApi()
      }
      route("swagger") {
        swaggerUI("/api/openapi.json")
      }
    }
  }
}
