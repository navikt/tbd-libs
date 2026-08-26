package no.nav.helse.speil.backend.app.auth

class TilgangsgrupperTilTilganger(
    private val tilgangLesGruppeId: String,
    private val tilgangSkrivGruppeId: String,
) {
    /**
     * Skrivetilgang impliserer lesetilgang: en saksbehandler i skrivegruppa er ikke nødvendigvis
     * medlem av lesegruppa i Entra, og ville ellers fått 403 på alle `Tilgang.Les`-endepunkter.
     * Samme semantikk som spesialist (`TilgangsgrupperTilTilganger.finnTilgangerFraTilgangsgrupper`).
     */
    fun tilganger(entraGrupper: Set<String>): Set<Tilgang> =
        buildSet {
            if (tilgangSkrivGruppeId in entraGrupper) {
                add(Tilgang.Skriv)
                add(Tilgang.Les)
            }
            if (tilgangLesGruppeId in entraGrupper) add(Tilgang.Les)
        }

    companion object {
        fun fraEnv(env: Map<String, String> = System.getenv()) =
            TilgangsgrupperTilTilganger(
                tilgangLesGruppeId = env.getValue("TILGANG_LES"),
                tilgangSkrivGruppeId = env.getValue("TILGANG_SKRIV"),
            )
    }
}
