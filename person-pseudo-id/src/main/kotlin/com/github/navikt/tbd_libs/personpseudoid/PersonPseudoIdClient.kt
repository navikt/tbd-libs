package com.github.navikt.tbd_libs.personpseudoid

import io.valkey.JedisPooled
import java.time.Duration
import java.util.*

data class ValkeyConfig(
    val username: String,
    val password: String,
    val connectionString: String,
) {
    fun connectionStringMedBrukernavnOgPassord(): String {
        val deltITo = connectionString.split("://")
        return deltITo[0] + "://" + username + ":" + password + "@" + deltITo[1]
    }
}

class PersonPseudoIdClient(
    valkeyConfig: ValkeyConfig,
) {
    private val jedisPooled =
        JedisPooled(valkeyConfig.connectionStringMedBrukernavnOgPassord())

    fun nyPersonPseudoId(identitetsnummer: String): PersonPseudoId {
        val nyId = UUID.randomUUID()
        jedisPooled.setex(nyId.toString(), Duration.ofDays(7).seconds, identitetsnummer)
        return PersonPseudoId(nyId)
    }

    fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): String? {
        return jedisPooled.get(personPseudoId.value.toString())
    }
}


@JvmInline
value class PersonPseudoId(val value: UUID)
