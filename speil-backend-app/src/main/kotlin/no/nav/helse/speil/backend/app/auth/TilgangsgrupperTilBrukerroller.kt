package no.nav.helse.speil.backend.app.auth

class TilgangsgrupperTilBrukerroller<ROLLE : Brukerrolle>(
    private val gruppeTilRolle: Map<String, ROLLE>,
) {
    fun brukerroller(entraGrupper: Set<String>): Set<ROLLE> = entraGrupper.mapNotNullTo(mutableSetOf()) { gruppeTilRolle[it] }
}
