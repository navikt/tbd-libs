package com.github.navikt.tbd_libs.personpseudoid

import io.valkey.Connection
import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.JedisPooled
import java.time.Duration
import java.util.*
import org.apache.commons.pool2.impl.GenericObjectPoolConfig

data class ValkeyConfig(
    val username: String,
    val password: String,
    private val connectionString: String,
) {
    private val parsedUri = java.net.URI(connectionString)
    val host: String = parsedUri.host
    val port: Int = parsedUri.port
    val ssl: Boolean = parsedUri.scheme in listOf("valkeys", "rediss")
}

class PersonPseudoIdClient(
    valkeyConfig: ValkeyConfig,
) {
    private val jedisPooled =
        JedisPooled(
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig
                .builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.username)
                .password(valkeyConfig.password)
                .build(),
            GenericObjectPoolConfig<Connection>().apply {
                testOnBorrow = true
                testWhileIdle = true
                timeBetweenEvictionRuns = Duration.ofSeconds(30)
                minEvictableIdleDuration = Duration.ofMinutes(1)
            }
        )

    fun nyPersonPseudoId(identitetsnummer: String): UUID {
        val nyId = UUID.randomUUID()
        jedisPooled.setex(nyId.toString(), Duration.ofDays(7).seconds, identitetsnummer)
        return nyId
    }

    fun finnIdentitetsnummer(personPseudoId: UUID): String? {
        return jedisPooled.get(personPseudoId.toString())
    }
}
