package com.github.navikt.tbd_libs.personpseudoid

import java.util.UUID

@JvmInline
value class PersonPseudoId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun fraString(raw: String): PersonPseudoId? = runCatching { PersonPseudoId(UUID.fromString(raw)) }.getOrNull()
    }
}
