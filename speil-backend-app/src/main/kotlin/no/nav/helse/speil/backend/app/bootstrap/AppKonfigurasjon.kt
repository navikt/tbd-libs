package no.nav.helse.speil.backend.app.bootstrap

import no.nav.helse.speil.backend.app.auth.AzureAdConfig
import no.nav.helse.speil.backend.app.auth.TilgangsgrupperTilTilganger
import no.nav.helse.speil.backend.app.db.DatabaseConfig
import no.nav.helse.speil.backend.app.openapi.OpenApiConfig
import no.nav.helse.speil.backend.app.person.PopulasjonstilgangConfig

data class AppKonfigurasjon(
    val appNavn: String,
    val azureAd: AzureAdConfig,
    val database: DatabaseConfig,
    val populasjonstilgang: PopulasjonstilgangConfig,
    val tilganger: TilgangsgrupperTilTilganger,
    val openApi: OpenApiConfig,
    val valkeyInstansPersonPseudoId: String = "personpseudoid",
) {
    companion object {
        fun fraEnv(
            appNavn: String,
            env: Map<String, String> = System.getenv(),
        ) = AppKonfigurasjon(
            appNavn = appNavn,
            azureAd = AzureAdConfig.fraEnv(env),
            database = DatabaseConfig.fraEnv(env),
            populasjonstilgang = PopulasjonstilgangConfig.fraEnv(env),
            tilganger = TilgangsgrupperTilTilganger.fraEnv(env),
            openApi = OpenApiConfig.fraEnv(appNavn, env),
        )
    }
}
