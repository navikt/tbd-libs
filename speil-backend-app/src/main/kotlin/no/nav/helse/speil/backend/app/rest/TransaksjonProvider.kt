package no.nav.helse.speil.backend.app.rest

interface TransaksjonProvider<TRANSAKSJON> {
    fun <T> transaksjon(block: (TRANSAKSJON) -> T): T
}
