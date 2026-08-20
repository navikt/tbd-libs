package no.nav.helse.speil.backend.app.auth

class TilgangsgrupperTilTilganger(
    private val tilgangLesGruppeId: String,
    private val tilgangSkrivGruppeId: String,
) {
    fun tilganger(entraGrupper: Set<String>): Set<Tilgang> =
        buildSet {
            if (tilgangLesGruppeId in entraGrupper) add(Tilgang.Les)
            if (tilgangSkrivGruppeId in entraGrupper) add(Tilgang.Skriv)
        }

    companion object {
        fun fraEnv(env: Map<String, String> = System.getenv()) =
            TilgangsgrupperTilTilganger(
                tilgangLesGruppeId = env.getValue("TILGANG_LES"),
                tilgangSkrivGruppeId = env.getValue("TILGANG_SKRIV"),
            )
    }
}
