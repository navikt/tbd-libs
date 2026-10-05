package com.github.navikt.tbd_libs.personpseudoid

interface PersonPseudoIdProvider {
    fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId

    fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer?
}
