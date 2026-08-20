package no.nav.helse.speil.backend.app.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import no.nav.helse.speil.backend.app.auditlogg.AuditloggUtfall
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.SaksbehandlerPrincipal
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.person.PersonPseudoIdProvider
import no.nav.helse.speil.backend.app.person.PersonResource

class RestAdapter<ROLLE : Brukerrolle, TRANSAKSJON>(
    private val personPseudoIdProvider: PersonPseudoIdProvider,
    private val populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
    private val auditlogger: Auditlogger,
    private val transaksjonProvider: TransaksjonProvider<TRANSAKSJON>,
) {
    suspend fun <RESOURCE : Any, RESPONSE, ERROR : ApiErrorCode> håndter(
        call: ApplicationCall,
        resource: RESOURCE,
        behandler: RestBehandler<ROLLE>,
        kjørBehandler: (resource: RESOURCE, kallKontekst: KallKontekst<TRANSAKSJON, ROLLE>) -> RestResponse<RESPONSE, ERROR>,
    ) {
        val principal =
            call.principal<SaksbehandlerPrincipal<ROLLE>>()
                ?: return call.respondProblem(RammeverkFeilkode.Uautentisert)

        if (behandler.påkrevdTilgang !in principal.tilganger) {
            return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
        }
        if (!principal.brukerroller.containsAll(behandler.påkrevdeBrukerroller)) {
            return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
        }

        if (resource is PersonResource) {
            val identitetsnummer =
                personPseudoIdProvider.finnIdentitetsnummer(PersonPseudoId(resource.pseudoId))
                    ?: return call.respondProblem(RammeverkFeilkode.PersonIkkeFunnet)

            when (
                val tilgangsresultat =
                    populasjonstilgangskontrollProvider.kontrollerKomplettTilgang(
                        principal.accessToken.value,
                        identitetsnummer.value,
                    )
            ) {
                is TilgangskontrollResultat.Ok -> {
                    auditlogger.loggPersonoppslag(principal.saksbehandler.navIdent, AuditloggUtfall.Permit)
                }
                is TilgangskontrollResultat.ManglerTilgang -> {
                    auditlogger.loggPersonoppslag(
                        principal.saksbehandler.navIdent,
                        AuditloggUtfall.Deny,
                        begrunnelse = "manglerTilgang=${tilgangsresultat.tilgangSomMangler}",
                    )
                    return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
                }
                is TilgangskontrollResultat.IdentIkkeFunnet -> {
                    auditlogger.loggPersonoppslag(principal.saksbehandler.navIdent, AuditloggUtfall.Deny, begrunnelse = "identIkkeFunnet")
                    return call.respondProblem(RammeverkFeilkode.PersonIkkeFunnet)
                }
                is TilgangskontrollResultat.UventetFeil -> {
                    auditlogger.loggPersonoppslag(principal.saksbehandler.navIdent, AuditloggUtfall.Deny, begrunnelse = "uventetFeil")
                    return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
                }
            }
        }

        val restResponse =
            transaksjonProvider.transaksjon { transaksjon ->
                val kallKontekst =
                    KallKontekst(
                        saksbehandler = principal.saksbehandler,
                        tilganger = principal.tilganger,
                        brukerroller = principal.brukerroller,
                        transaksjon = transaksjon,
                        accessToken = principal.accessToken,
                        personPseudoIdProvider = personPseudoIdProvider,
                        populasjonstilgangskontrollProvider = populasjonstilgangskontrollProvider,
                        auditlogger = auditlogger,
                    )
                kjørBehandler(resource, kallKontekst)
            }

        when (restResponse) {
            is RestResponse.Ok -> call.respond(HttpStatusCode.OK, restResponse.body as Any)
            is RestResponse.Feil -> call.respondProblem(restResponse.feil, restResponse.detalj)
        }
    }

    private suspend fun ApplicationCall.respondProblem(
        feil: ApiErrorCode,
        detalj: String? = null,
    ) {
        respond(
            HttpStatusCode.fromValue(feil.httpStatus),
            ProblemDetails(
                title = feil.tittel,
                status = feil.httpStatus,
                detail = detalj,
                instance = request.uri,
            ),
        )
    }
}
