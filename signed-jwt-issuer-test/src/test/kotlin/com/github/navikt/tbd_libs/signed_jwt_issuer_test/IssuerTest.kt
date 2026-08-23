package com.github.navikt.tbd_libs.signed_jwt_issuer_test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

@TestInstance(PER_CLASS)
class IssuerTest {
    private val issuer = Issuer(navn = "Min nydlige issuer", "http://nydlige-issuer")

    @BeforeAll fun setup() {
        issuer.start()
    }

    @AfterAll fun teardown() {
        issuer.stop()
    }

    @Test
    fun `et token uten noe tull eller ball`() {
        val token = issuer.accessToken()
        token.assertHeaders {
            assertEquals("key-1234", path("kid").asString())
            assertEquals("JWT", path("typ").asString())
            assertEquals("RS256", path("alg").asString())
        }
        token.assertPayload {
            assertEquals("Min nydlige issuer", path("iss").asString())
            assertEquals("http://nydlige-issuer", path("aud").asString())
            assertTrue(path("exp").isNumber)
            assertTrue(path("iat").isNumber)
        }
    }

    @Test
    fun `et token som overskriver alle defaults`() {
        val token =
            issuer.accessToken {
                withIssuer("issuer2")
                withAudience("audience2")
                withKeyId("kid2")
                withHeader(mapOf("typ" to "typ2"))
                withClaim("iat", "iat2")
                withClaim("exp", "exp2")
            }
        token.assertHeaders {
            assertEquals("kid2", path("kid").asString())
            assertEquals("typ2", path("typ").asString())
        }
        token.assertPayload {
            assertEquals("issuer2", path("iss").asString())
            assertEquals("audience2", path("aud").asString())
            assertEquals("iat2", path("iat").asString())
            assertEquals("exp2", path("exp").asString())
        }
    }

    @Test
    fun `et token med litt snaxne claims`() {
        val token =
            issuer.accessToken {
                withArrayClaim("roles", arrayOf("rolle1", "rolle2", "rolle3"))
                withArrayClaim("noe", arrayOf(1L, 2L, 3L))
                withNullClaim("nully")
            }
        token.assertPayload {
            assertEquals(listOf("rolle1", "rolle2", "rolle3"), path("roles").values().map { it.asString() })
            assertEquals(listOf(1, 2, 3), path("noe").values().map { it.asInt() })
            assertTrue(path("nully").isNull)
        }
    }

    @Test
    fun `hente jwks`() {
        val response =
            with(HttpClient.newHttpClient()) {
                val request = HttpRequest.newBuilder(issuer.jwksUri()).GET().build()
                send(request, HttpResponse.BodyHandlers.ofString())
            }
        assertEquals(200, response.statusCode())
        val json = JsonMapper().readTree(response.body())
        val keys = json.path("keys")
        assertEquals(1, keys.size())
        assertEquals("key-1234", keys.single().path("kid").asString())
    }

    @Test
    fun `gå mot well-known`() {
        val response =
            with(HttpClient.newHttpClient()) {
                val request = HttpRequest.newBuilder(issuer.wellKnownUri()).GET().build()
                send(request, HttpResponse.BodyHandlers.ofString())
            }
        assertEquals(200, response.statusCode())
        val json = (JsonMapper().readTree(response.body()) as ObjectNode)
        assertEquals(setOf("issuer", "jwks_uri"), json.propertyNames().toSet())
        assertEquals("Min nydlige issuer", json.path("issuer").asString())
        assertEquals("${issuer.jwksUri()}", json.path("jwks_uri").asString())
    }

    @Test
    fun `Kunne hente URL'er før issuer er startet opp`() {
        with(Issuer("hei", "HEI")) {
            assertFalse(startet())
            jwksUri()
            wellKnownUri()
        }
    }

    private fun String.assertHeaders(assertions: JsonNode.() -> Unit) = JsonMapper().readTree(Base64.getDecoder().decode(this.split(".")[0])).apply(assertions)

    private fun String.assertPayload(assertions: JsonNode.() -> Unit) = JsonMapper().readTree(Base64.getDecoder().decode(this.split(".")[1])).apply(assertions)
}
