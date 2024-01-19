package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*

class JwkTest {

    @Test
    fun `Generere client assertion fra JWK`() {
        val jwk: Map<String, Any?> = om.readValue(TEST_JWK)
        val authMethod = AzureAuthMethod.Jwk(jwk, "test-client-id", URI("http://localhost:8080/token"))
        val assertion = authMethod.requestParameters().getValue("client_assertion")
        val parts = assertion.split(".")
        assertEquals(3, parts.size)
        val header = parts[0]
        val claims = parts[1]
        val signature = parts[2]

        // Header
        assertEquals("""{"kid":"test-jwk","typ":"JWT","alg":"RS256"}""", String(base64Decoder.decode(header)))

        // Claims
        val claimsMap: Map<String, String> = om.readValue(String(base64Decoder.decode(claims)))
        assertEquals(setOf("aud", "sub", "nbf", "iss", "exp", "iat", "jti"), claimsMap.keys)
        assertEquals("test-client-id", claimsMap.getValue("sub"))
        assertEquals("test-client-id", claimsMap.getValue("iss"))
        assertEquals("http://localhost:8080/token", claimsMap.getValue("aud"))
        assertDoesNotThrow { UUID.fromString(claimsMap.getValue("jti")) }
        val expiry = assertDoesNotThrow { claimsMap.getValue("exp").toLong() }
        val issuedAt = assertDoesNotThrow { claimsMap.getValue("iat").toLong() }
        val notBefore = assertDoesNotThrow { claimsMap.getValue("nbf").toLong() }
        assertEquals(issuedAt, notBefore)
        assertTrue(expiry > issuedAt)

        // Signature
        val verifyer = Signature.getInstance("SHA256withRSA")
        verifyer.initVerify(publicKey(jwk))
        verifyer.update("$header.$claims".toByteArray())
        assertTrue(verifyer.verify(base64Decoder.decode(signature)))
    }

    private companion object {

        private val om = jacksonObjectMapper()
        private val base64Decoder = Base64.getUrlDecoder()
        private val String.decodeToBigInteger get() = BigInteger(1, base64Decoder.decode(this))

        @Language("JSON")
        private const val TEST_JWK = """
        {
            "kid": "test-jwk",
            "kty": "RSA",
            "n": "pBjxqwjKaM4YG3Kf9dRugVjWGFLT6w4tdHRaQoipaTzl_891DMx6ccuUMMbjTxdxevsDbYOB0fjcKHHXQ9JW5yVaBxl2hk7FIre3uDeLOqNbbpr7mekwGzqz4YGAkTpNjDoljxS-5v3Dxo5Zr85FFXpdoed4Vs37p3U7FAlc91sZ0TJ0BV1q5k-kkG6UmsEsdp1qZxNsQ_5K1nWxREDxaBUOiIfDfPiHmmRXHEEEKY_AQ00-i97SC4vMu4cW9tCKxiBpKh743qF-GkctCePol5PCjFpy56PFC4PnZjRFgnn80kdSbFOwH8l07unzOiUzKlWkv0b5WXw8h3ydZFFexw",
            "d": "gRm-x7iaxemevblob5c5eTnS9j_zybHVwRDpEf9CiTEIIkGs7OzSSETJybYvj0H6Xa6t-7LCp9cKHieyHAGXrTKNqZg2z2OZZL71I1FPkEqE3HfCCkyTNFjyvC-OXrNn3zK_6dmAd2qeY9AKb23wm_0xPPdGjcRwgEaSvCjBozgd8dKgrn8bnALb1V1mGPZt5X648723uW6zBqO94ue73gqp7WrE2AMTG4SaiX-CzO4dSzLI6AUZGnBfF6umyxrZBFR6g2m1zATBa5i0YXrIHXM3RREnFNmcOrcNO3borzNtZCiMW7ZrXIqO8AVnDjNmVzbg5v3f9Ol1U6t1TT0XAQ"
        }
        """

        private fun publicKey(jwk: Map<String, Any?>): RSAPublicKey {
            val modulus = jwk.getValue("n").toString().decodeToBigInteger
            val exponent = "AQAB".decodeToBigInteger
            val spec = RSAPublicKeySpec(modulus, exponent)
            val factory = KeyFactory.getInstance("RSA")
            return factory.generatePublic(spec) as RSAPublicKey
        }
    }
}