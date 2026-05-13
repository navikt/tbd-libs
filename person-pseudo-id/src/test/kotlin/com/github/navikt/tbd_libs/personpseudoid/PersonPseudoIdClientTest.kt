package com.github.navikt.tbd_libs.personpseudoid

import io.valkey.DefaultJedisClientConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import io.valkey.HostAndPort
import io.valkey.JedisPooled
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonPseudoIdClientTest {
    private val valkeyInstance = GenericContainer(DockerImageName.parse("valkey/valkey:latest")).apply {
        withExposedPorts(6379)
        withCommand("valkey-server", "--requirepass", "password")
        start()
    }
    private val valkeyConfig = ValkeyConfig(
        username = "default",
        password = "password",
        connectionString = "valkey://${valkeyInstance.host}:${valkeyInstance.getMappedPort(6379)}",
    )
    private val client = PersonPseudoIdClient(valkeyConfig)

    private val jedisPooled =
        JedisPooled(
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig
                .builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.username)
                .password(valkeyConfig.password)
                .build(),
        )

    @AfterAll
    fun tearDown() {
        jedisPooled.close()
        valkeyInstance.stop()
    }

    @Test
    fun `kan lagre ny personPseudoId og finne identitetsnummer basert på den`() {
        val identitetsnummer1 = "12345678910"
        val identitetsnummer2 = "12345678911"
        val nyId1 = client.nyPersonPseudoId(identitetsnummer1)
        val nyId2 = client.nyPersonPseudoId(identitetsnummer2)
        val funnet1 = client.finnIdentitetsnummer(nyId1)
        val funnet2 = client.finnIdentitetsnummer(nyId2)
        assertEquals(identitetsnummer1, funnet1)
        assertEquals(identitetsnummer2, funnet2)
    }

    @Test
    fun `sjekk ttl`() {
        val identitetsnummer = "12345678912"
        val nyId = client.nyPersonPseudoId(identitetsnummer)
        val ttl = jedisPooled.ttl(nyId.toString())
        assertEquals(ttl, Duration.ofDays(7).seconds)
    }
}

