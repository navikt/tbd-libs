package no.nav.helse.speil.backend.app.bootstrap

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.routing.Routing
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
    rivere: RapidsConnection.(TransaksjonProvider<TRANSAKSJON>) -> Unit = {},
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit = {},
) {
    val dataSource = konfigurasjon.database.dataSource()

    migrerSynkront(konfigurasjon.database)

    RapidApplication
        .create(env, builder = {
            withKtorModule {
                speilBackendApp(
                    konfigurasjon = konfigurasjon,
                    brukerroller = brukerroller,
                    transaksjonProvider = transaksjonProvider(dataSource),
                    endepunkter = endepunkter,
                    env = env
                )
            }
        })
        .apply { rivere(transaksjonProvider(dataSource)) }
        .start()
}

// TODO: Trekke dette ut i et eget lib, slik at vi kan bruke denne uten å dra inn rapids and rivers
fun <ROLLE : Brukerrolle, TRANSAKSJON> Application.speilBackendApp(
    konfigurasjon: AppKonfigurasjon,
    brukerroller: TilgangsgrupperTilBrukerroller<*>,
    transaksjonProvider: TransaksjonProvider<TRANSAKSJON>,
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit,
    env: Map<String, String> = System.getenv(),
    ekstraRouting: Routing.() -> Unit = {},
    ekstraAuthenticationConfig: AuthenticationConfig.() -> Unit = {},
) {
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
            transaksjonProvider = transaksjonProvider,
        )
    configureCallId()
    configureCallLogging()
    configureContentNegotiation()
    configureStatusPages()
    configureResources()
    configureJwtAuthentication(
        azureAdConfig = konfigurasjon.azureAd,
        tilgangsgrupperTilTilganger = konfigurasjon.tilganger,
        tilgangsgrupperTilBrukerroller = brukerroller,
        ekstraAuthenticationConfig = ekstraAuthenticationConfig
    )
    configureOpenApiPlugin(konfigurasjon.openApi)
    configureRestRuting(restAdapter, endepunkter, ekstraRouting)
    monitor.subscribe(ApplicationStarted) {
        loggInfo("Ktor-applikasjon startet for ${konfigurasjon.appNavn}")
    }
}
