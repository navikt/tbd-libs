package no.nav.helse.speil.backend.app.rest

interface ApiErrorCode {
    val httpStatus: Int
    val tittel: String
}
