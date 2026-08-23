package no.nav.helse.speil.backend.app.person

interface PersonPseudoIdProvider {
    fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId

    fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer?
}
