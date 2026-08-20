package no.nav.helse.speil.backend.app.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import no.nav.helse.speil.backend.app.serialization.customSerializersModule

fun Application.configureResources() {
    install(Resources) {
        serializersModule = customSerializersModule
    }
}
