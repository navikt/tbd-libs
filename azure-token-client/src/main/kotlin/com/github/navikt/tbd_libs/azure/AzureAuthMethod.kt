package com.github.navikt.tbd_libs.azure

import org.intellij.lang.annotations.Language
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateCrtKeySpec
import java.time.Clock
import java.time.Instant
import java.util.*

sealed interface AzureAuthMethod {
    fun requestParameters(): Map<String, String>


    class Secret(private val secret: String) : AzureAuthMethod {
        override fun requestParameters(): Map<String, String> {
            return mapOf(
                "client_secret" to secret
            )
        }
    }
    class Jwt(private val jwt: String) : AzureAuthMethod {
        override fun requestParameters(): Map<String, String> {
            return mapOf(
                "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion" to jwt
            )
        }
    }

    class Jwk private constructor(
        private val privateKey: RSAPrivateKey,
        keyId: String,
        private val clientId: String,
        private val tokenEndpoint: URI
    ): AzureAuthMethod {
        private val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
        }

        @Language("JSON")
        private val header = """{"kid":"$keyId","typ":"JWT","alg":"RS256"}""".let { base64Encoder.encodeToString(it.toByteArray()) }
        private val claims get() : String {
            val now = Instant.now(Clock.systemUTC())
            val expiry = now.plusSeconds(10)
            @Language("JSON") val json = """{"aud":"$tokenEndpoint","sub":"$clientId","nbf":${now.epochSecond},"iss":"$clientId","exp":${expiry.epochSecond},"iat":${now.epochSecond},"jti":"${UUID.randomUUID()}"}"""
            return base64Encoder.encodeToString(json.toByteArray())
        }

        constructor(jwk: Map<String, Any?>, clientId: String, tokenEndpoint: URI): this(jwk.somRSAPrivateKey(), jwk.hent("kid", "Key ID"), clientId, tokenEndpoint)

        private fun assertion(): String {
            val signingInput = "$header.$claims"
            signature.update(signingInput.toByteArray())
            val signature = base64Encoder.encodeToString(signature.sign())
            return "$signingInput.$signature"
        }
        override fun requestParameters() = Jwt(assertion()).requestParameters()

        private companion object {
            private val base64Decoder = Base64.getUrlDecoder()
            private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
            private val String.decodeToBigInteger get() = BigInteger(1, base64Decoder.decode(this))
            private fun Map<String, Any?>.hent(key: String, navn: String) = checkNotNull(get(key)?.takeIf { it is String }?.let { it as String }) { "$navn ($key) må være satt. Fant kun $keys" }
            private fun Map<String, Any?>.somRSAPrivateKey(): RSAPrivateKey {
                val keyType = hent("kty", "Key type")
                check(keyType.uppercase() == "RSA") { "Key type (kty) må være RSA" }
                val modulus = hent("n", "Modulus").decodeToBigInteger
                val privateExponent = hent("d", "Private exponent").decodeToBigInteger
                val publicExponent = hent("e", "Public exponent").decodeToBigInteger
                val primeP = hent("p", "Prime P").decodeToBigInteger
                val primeQ = hent("q", "Prime Q").decodeToBigInteger
                val primeExponentP = hent("dp", "Prime Exponent P").decodeToBigInteger
                val primeExponentQ = hent("dq", "Prime Exponent Q").decodeToBigInteger
                val crtCoefficient = hent("qi", "Certificate Coefficient").decodeToBigInteger
                val keySpec = RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent, primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient)
                val factory = KeyFactory.getInstance("RSA")
                return (factory.generatePrivate(keySpec) as RSAPrivateKey)
            }
        }
    }
}