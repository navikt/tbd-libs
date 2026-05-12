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

    fun nyPersonPseudoId(identitetsnummer: String): UUID {
        val nyId = UUID.randomUUID()
        jedisPooled.setex(nyId.toString(), Duration.ofDays(7).seconds, identitetsnummer)
        return nyId
    }

    fun finnIdentitetsnummer(personPseudoId: UUID): String? {
        return jedisPooled.get(personPseudoId.toString())
    }
}
