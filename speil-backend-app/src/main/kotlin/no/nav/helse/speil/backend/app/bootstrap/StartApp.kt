package no.nav.helse.speil.backend.app.bootstrap

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.ApplicationStarted
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.TilgangsgrupperTilBrukerroller
import no.nav.helse.speil.backend.app.auth.configureJwtAuthentication
import no.nav.helse.speil.backend.app.db.dataSource
import no.nav.helse.speil.backend.app.db.migrerSynkront
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.speil.backend.app.openapi.configureOpenApiPlugin
import no.nav.helse.speil.backend.app.person.PersonPseudoIdProvider
import no.nav.helse.speil.backend.app.person.ValkeyPersonPseudoIdProvider
import no.nav.helse.speil.backend.app.person.tilgangsmaskinenClient
import no.nav.helse.speil.backend.app.plugins.configureCallId
import no.nav.helse.speil.backend.app.plugins.configureCallLogging
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.plugins.configureStatusPages
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.RestRuting
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.speil.backend.app.rest.configureRestRuting
import javax.sql.DataSource

fun <ROLLE : Brukerrolle, TRANSAKSJON> startApp(
    konfigurasjon: AppKonfigurasjon,
    brukerroller: TilgangsgrupperTilBrukerroller<ROLLE>,
    transaksjonProvider: (DataSource) -> TransaksjonProvider<TRANSAKSJON>,
    env: Map<String, String> = System.getenv(),
    rivere: RapidsConnection.(DataSource) -> Unit = {},
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit = {},
) {
    val dataSource = konfigurasjon.database.dataSource()

    migrerSynkront(konfigurasjon.database)

    val texasClient = TexasClient.fromEnv()
    val populasjonstilgangskontrollProvider = konfigurasjon.populasjonstilgang.tilgangsmaskinenClient(texasClient)
    val personPseudoIdProvider: PersonPseudoIdProvider =
        ValkeyPersonPseudoIdProvider.fraEnv(konfigurasjon.valkeyInstansPersonPseudoId, env)
    val auditlogger = Auditlogger(konfigurasjon.appNavn)
    val restAdapter =
        RestAdapter<ROLLE, TRANSAKSJON>(
            personPseudoIdProvider = personPseudoIdProvider,
            populasjonstilgangskontrollProvider = populasjonstilgangskontrollProvider,
            auditlogger = auditlogger,
            transaksjonProvider = transaksjonProvider(dataSource),
        )

    RapidApplication
        .create(env, builder = {
            withKtorModule {
                configureCallId()
                configureCallLogging()
                configureContentNegotiation()
                configureStatusPages()
                configureResources()
                configureJwtAuthentication(
                    azureAdConfig = konfigurasjon.azureAd,
                    tilgangsgrupperTilTilganger = konfigurasjon.tilganger,
                    tilgangsgrupperTilBrukerroller = brukerroller,
                )
                configureOpenApiPlugin(konfigurasjon.openApi)
                configureRestRuting(restAdapter, endepunkter)
                monitor.subscribe(ApplicationStarted) {
                    loggInfo("Ktor-applikasjon startet for ${konfigurasjon.appNavn}")
                }
            }
        })
        .apply { rivere(dataSource) }
        .start()
}
