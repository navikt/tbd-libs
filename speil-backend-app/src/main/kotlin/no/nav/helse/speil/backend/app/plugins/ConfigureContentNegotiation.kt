package no.nav.helse.speil.backend.app.plugins

import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import tools.jackson.databind.cfg.DateTimeFeature

fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        jackson {
            disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}
