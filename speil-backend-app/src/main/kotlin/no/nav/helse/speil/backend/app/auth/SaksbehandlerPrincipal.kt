package no.nav.helse.speil.backend.app.auth


data class SaksbehandlerPrincipal<ROLLE : Brukerrolle>(
    val saksbehandler: Saksbehandler,
    val tilganger: Set<Tilgang>,
    val brukerroller: Set<ROLLE>,
    val accessToken: AccessToken,
)
