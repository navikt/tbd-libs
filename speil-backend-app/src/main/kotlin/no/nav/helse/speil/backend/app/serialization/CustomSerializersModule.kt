package no.nav.helse.speil.backend.app.serialization

import kotlinx.serialization.modules.SerializersModule
import java.util.UUID


val customSerializersModule =
    SerializersModule {
        contextual(UUID::class, UUIDStringSerializer)
    }
