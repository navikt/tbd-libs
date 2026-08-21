package no.nav.helse.speil.backend.app.serialization

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.modules.SerializersModule
import java.util.UUID


val customSerializersModule =
    SerializersModule {
        contextual(BigDecimal::class, BigDecimalStringSerializer)
        contextual(Boolean::class, BooleanStrictSerializer)
        contextual(Instant::class, InstantIsoSerializer)
        contextual(LocalDate::class, LocalDateIsoSerializer)
        contextual(LocalDateTime::class, LocalDateTimeIsoSerializer)
        contextual(UUID::class, UUIDStringSerializer)
    }
