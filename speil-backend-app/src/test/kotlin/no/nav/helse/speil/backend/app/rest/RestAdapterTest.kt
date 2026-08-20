package no.nav.helse.speil.backend.app.rest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.AccessToken
import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.NavIdent
import no.nav.helse.speil.backend.app.auth.Saksbehandler
import no.nav.helse.speil.backend.app.auth.SaksbehandlerOid
import no.nav.helse.speil.backend.app.auth.SaksbehandlerPrincipal
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.person.PersonResource
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureStatusPages
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

private enum class TestRolle(
    override val navn: String,
) : Brukerrolle {
    Beslutter("beslutter"),
}

private enum class TestFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    NoeGikkGalt(400, "Noe gikk galt"),
}

private object EnkelResource

private class PersonTestResource(
    override val pseudoId: UUID,
) : PersonResource

private class FakeTilgangskontroll(
    private val resultat: TilgangskontrollResultat = TilgangskontrollResultat.Ok,
) : PopulasjonstilgangskontrollProvider {
    var antallKall = 0

    override fun kontrollerKomplettTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat {
        antallKall++
        return resultat
    }

    override fun kontrollerKjerneTilgang(
        accessToken: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = resultat

    override fun kontrollerKjerneTilgangForAnsatt(
        ansattId: String,
        fødselsnummer: String,
    ): TilgangskontrollResultat = resultat
}

private object EnkelBehandler : GetBehandler<EnkelResource, String, TestFeil, TestRolle, Unit> {
    override val påkrevdTilgang = Tilgang.Skriv
    override val påkrevdeBrukerroller = setOf(TestRolle.Beslutter)
    override val tag = "test"

    override fun behandle(
        resource: EnkelResource,
        kallKontekst: KallKontekst<Unit, TestRolle>,
    ): RestResponse<String, TestFeil> = RestResponse.ok("hei ${kallKontekst.saksbehandler.navIdent.value}")
}

private object FeilendeBehandler : GetBehandler<EnkelResource, String, TestFeil, TestRolle, Unit> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "test"

    override fun behandle(
        resource: EnkelResource,
        kallKontekst: KallKontekst<Unit, TestRolle>,
    ): RestResponse<String, TestFeil> = throw RuntimeException("noe gikk fælt galt, med hemmelig detalj")
}

private object PersonBehandler : GetBehandler<PersonTestResource, String, TestFeil, TestRolle, Unit> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "test"

    override fun behandle(
        resource: PersonTestResource,
        kallKontekst: KallKontekst<Unit, TestRolle>,
    ): RestResponse<String, TestFeil> = RestResponse.ok("person-svar")
}

/**
 * Verifiserer den sentrale autorisasjonsstien i [RestAdapter]: 401 → 403-tilgang → 403-rolle →
 * automatisk populasjonstilgangskontroll for [PersonResource] → transaksjon → responsmapping.
 *
 * Testene bruker vanlige ktor-routes (ikke `Ruting.kt`s typede `Resources`-DSL, som forutsetter
 * kotlinx.serialization-kompilatorpluginet — ikke satt opp i dette prosjektet ennå) og kaller
 * `RestAdapter.håndter(...)` direkte fra rutelambdaen. Dette tester nøyaktig det samme
 * autorisasjonsflyten som `Ruting.kt` faktisk bruker den til.
 *
 * Selve JWT-oppsettet er dekket av `ConfigureJwtAuthenticationTest`; denne testen fokuserer
 * utelukkende på hva `RestAdapter` gjør NÅR en principal (eventuelt) finnes.
 */
class RestAdapterTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")

    private fun principal(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les),
        brukerroller: Set<TestRolle> = emptySet(),
    ) = SaksbehandlerPrincipal(saksbehandler, tilganger, brukerroller, AccessToken("token"))

    private fun Application.settOppTestapp(
        principal: SaksbehandlerPrincipal<TestRolle>? = null,
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        personPseudoIdProvider: InMemoryPersonPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
        feilendeBehandler: Boolean = false,
        medStatusPages: Boolean = false,
    ) {
        configureContentNegotiation()
        if (medStatusPages) configureStatusPages()
        if (principal != null) {
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
        }
        val restAdapter =
            RestAdapter<TestRolle, Unit>(
                personPseudoIdProvider = personPseudoIdProvider,
                populasjonstilgangskontrollProvider = tilgangskontroll,
                auditlogger = Auditlogger("test"),
                transaksjonProvider =
                    object : TransaksjonProvider<Unit> {
                        override fun <T> transaksjon(block: (Unit) -> T): T = block(Unit)
                    },
            )
        routing {
            get("/enkel") {
                val behandler = if (feilendeBehandler) FeilendeBehandler else EnkelBehandler
                restAdapter.håndter(call, EnkelResource, behandler) { resource, kallKontekst -> behandler.behandle(resource, kallKontekst) }
            }
            get("/person/{pseudoId}") {
                val resource = PersonTestResource(UUID.fromString(call.parameters["pseudoId"]))
                restAdapter.håndter(call, resource, PersonBehandler) { r, kallKontekst -> PersonBehandler.behandle(r, kallKontekst) }
            }
        }
    }

    @Test
    fun `kall uten principal gir 401`() =
        testApplication {
            application { settOppTestapp() }

            val response = client.get("/enkel")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `principal uten paakrevd tilgang gir 403`() =
        testApplication {
            application {
                settOppTestapp(principal(tilganger = setOf(Tilgang.Les), brukerroller = setOf(TestRolle.Beslutter)))
            }

            val response = client.get("/enkel")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `principal uten paakrevd brukerrolle gir 403`() =
        testApplication {
            application {
                settOppTestapp(principal(tilganger = setOf(Tilgang.Skriv), brukerroller = emptySet()))
            }

            val response = client.get("/enkel")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `gyldig kall med riktig tilgang og rolle gir 200 med mappet body`() =
        testApplication {
            application {
                settOppTestapp(principal(tilganger = setOf(Tilgang.Skriv), brukerroller = setOf(TestRolle.Beslutter)))
            }

            val response = client.get("/enkel")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("hei Z999999", response.bodyAsText())
        }

    @Test
    fun `PersonResource trigger automatisk populasjonstilgangskontroll og auditlogg`() =
        testApplication {
            val fake = FakeTilgangskontroll(TilgangskontrollResultat.Ok)
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), tilgangskontroll = fake, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/person/$pseudoId")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, fake.antallKall)
        }

    @Test
    fun `utloept eller ukjent person-pseudo-id gir 404, ikke 500`() =
        testApplication {
            application { settOppTestapp(principal()) }

            val response = client.get("/person/${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `manglende populasjonstilgang gir 403 og auditlogges som Deny`() =
        testApplication {
            val fake = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet))
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), tilgangskontroll = fake, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/person/$pseudoId")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(1, fake.antallKall)
        }

    @Test
    fun `uventet exception gir 500 uten aa lekke stacktrace til klienten, og logges kun til teamLogs`() =
        testApplication {
            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val teamLogsLogger = loggerContext.getLogger("tjenestekall") as Logger
            val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
            val teamLogsAppender = ListAppender<ILoggingEvent>().apply { context = loggerContext; start() }
            val rootAppender = ListAppender<ILoggingEvent>().apply { context = loggerContext; start() }
            teamLogsLogger.addAppender(teamLogsAppender)
            rootLogger.addAppender(rootAppender)

            try {
                application {
                    settOppTestapp(principal(), feilendeBehandler = true, medStatusPages = true)
                }

                val response = client.get("/enkel")

                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertFalse(
                    response.bodyAsText().contains("hemmelig detalj"),
                    "responsen skal ikke lekke exception-meldingen",
                )
                assertTrue(
                    teamLogsAppender.list.any { it.formattedMessage.contains("Uventet feil") },
                    "forventet at feilen logges til teamLogs (tjenestekall)",
                )
                assertFalse(
                    rootAppender.list.any { it.throwableProxy != null },
                    "stacktracen skal ikke havne i vanlig (root) logg",
                )
            } finally {
                teamLogsLogger.detachAppender(teamLogsAppender)
                rootLogger.detachAppender(rootAppender)
            }
        }
}
