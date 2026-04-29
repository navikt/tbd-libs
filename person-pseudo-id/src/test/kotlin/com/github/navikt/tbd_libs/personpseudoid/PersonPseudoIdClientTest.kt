package com.github.navikt.tbd_libs.personpseudoid

import io.github.ss_bhatt.testcontainers.valkey.ValkeyContainer
import io.valkey.JedisPooled
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonPseudoIdClientTest {
    private val valkeyInstance = ValkeyContainer().apply {
        this.setCommand("--requirepass", "password")
        start()
    }
    private val valkeyConfig = ValkeyConfig(
        username = "default",
        password = "password",
        connectionString = valkeyInstance.connectionString
    )
    private val client = PersonPseudoIdClient(valkeyConfig)

    private val jedisPooled =
        JedisPooled(valkeyConfig.connectionStringMedBrukernavnOgPassord())

    @AfterAll
    fun tearDown() {
        jedisPooled.close()
        valkeyInstance.stop()
    }

    @Test
    fun `kan lagre ny personPseudoId og finne identitetsnummer basert på den`() {
        println(valkeyInstance.connectionString)
        val identitetsnummer1 = "12345678910"
        val identitetsnummer2 = "12345678911"
        val nyId1 = client.nyPersonPseudoId(identitetsnummer1)
        val nyId2 = client.nyPersonPseudoId(identitetsnummer2)
        val funnet1 = client.finnIdentitetsnummer(nyId1)
        val funnet2 = client.finnIdentitetsnummer(nyId2)
        Assertions.assertEquals(identitetsnummer1, funnet1)
        Assertions.assertEquals(identitetsnummer2, funnet2)
    }

    @Test
    fun `sjekk ttl`() {
        println(valkeyInstance.connectionString)
        val identitetsnummer = "12345678912"
        val nyId = client.nyPersonPseudoId(identitetsnummer)
        val ttl = jedisPooled.ttl(nyId.value.toString())
        Assertions.assertEquals(ttl, Duration.ofDays(7).seconds)
    }
}
