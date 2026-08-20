package no.nav.helse.speil.backend.app.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Sikrer deny-by-default og korrekt mapping fra Entra-gruppe-UUID-er til [Tilgang] FØR koden tas i
 * bruk mot ekte Entra-grupper.
 */
class TilgangsgrupperTilTilgangerTest {
    private val sut = TilgangsgrupperTilTilganger(tilgangLesGruppeId = "les-uuid", tilgangSkrivGruppeId = "skriv-uuid")

    @Test
    fun `gruppe-uuid som matcher TILGANG_SKRIV gir Tilgang Skriv`() {
        assertEquals(setOf(Tilgang.Skriv), sut.tilganger(setOf("skriv-uuid")))
    }

    @Test
    fun `ukjent gruppe-uuid gir tomt tilgangssett, ikke feil (deny-by-default)`() {
        assertEquals(emptySet<Tilgang>(), sut.tilganger(setOf("ukjent-uuid")))
    }

    @Test
    fun `tomt gruppesett gir tomt tilgangssett`() {
        assertEquals(emptySet<Tilgang>(), sut.tilganger(emptySet()))
    }

    @Test
    fun `saksbehandler i baade les- og skriv-gruppe faar begge tilganger`() {
        assertEquals(setOf(Tilgang.Les, Tilgang.Skriv), sut.tilganger(setOf("les-uuid", "skriv-uuid")))
    }
}
