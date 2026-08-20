package no.nav.helse.speil.backend.app.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import no.nav.helse.speil.backend.app.auditlogg.AuditloggUtfall
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.AccessToken
import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.Saksbehandler
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.person.PersonPseudoIdProvider


class KallKontekst<TRANSAKSJON, ROLLE : Brukerrolle>(
    val saksbehandler: Saksbehandler,
    val tilganger: Set<Tilgang>,
    val brukerroller: Set<ROLLE>,
    val transaksjon: TRANSAKSJON,
    val accessToken: AccessToken,
    private val personPseudoIdProvider: PersonPseudoIdProvider,
    private val populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
    private val auditlogger: Auditlogger,
) {
        fun <RESPONSE, ERROR : ApiErrorCode> medPerson(
        personPseudoId: PersonPseudoId,
        personIkkeFunnet: () -> ERROR,
        manglerTilgang: () -> ERROR,
        block: (Identitetsnummer) -> RestResponse<RESPONSE, ERROR>,
    ): RestResponse<RESPONSE, ERROR> {
        val identitetsnummer =
            personPseudoIdProvider.finnIdentitetsnummer(personPseudoId)
                ?: return RestResponse.feil(personIkkeFunnet())

        val tilgangsresultat =
            populasjonstilgangskontrollProvider.kontrollerKomplettTilgang(accessToken.value, identitetsnummer.value)

        return when (tilgangsresultat) {
            is TilgangskontrollResultat.Ok -> {
                auditlogger.loggPersonoppslag(saksbehandler.navIdent, AuditloggUtfall.Permit)
                block(identitetsnummer)
            }
            is TilgangskontrollResultat.ManglerTilgang -> {
                auditlogger.loggPersonoppslag(
                    saksbehandler.navIdent,
                    AuditloggUtfall.Deny,
                    begrunnelse = "manglerTilgang=${tilgangsresultat.tilgangSomMangler}",
                )
                RestResponse.feil(manglerTilgang())
            }
            is TilgangskontrollResultat.IdentIkkeFunnet -> {
                auditlogger.loggPersonoppslag(saksbehandler.navIdent, AuditloggUtfall.Deny, begrunnelse = "identIkkeFunnet")
                RestResponse.feil(personIkkeFunnet())
            }
            is TilgangskontrollResultat.UventetFeil -> {
                auditlogger.loggPersonoppslag(saksbehandler.navIdent, AuditloggUtfall.Deny, begrunnelse = "uventetFeil")
                RestResponse.feil(manglerTilgang())
            }
        }
    }
}
