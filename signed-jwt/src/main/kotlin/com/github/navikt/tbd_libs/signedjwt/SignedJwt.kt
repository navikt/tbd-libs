package com.github.navikt.tbd_libs.signedjwt

import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

class SignedJwt(private val privateKey: RSAPrivateKey, private val keyId: String) {
    constructor(jwk: Map<String, Any?>): this(jwk.somRSAPrivateKey(), jwk.hent("kid", "Key ID"))

    private val signature = Signature.getInstance("SHA256withRSA").apply {
        initSign(privateKey)
    }

    fun generate(headers: Map<String, Any> = emptyMap(), claims: Map<String, Any> = emptyMap()): String {
        val iat = claims["iat"]?.takeIf { it is Number }?.let { it as Long } ?: Instant.now(Clock.systemUTC()).epochSecond

        val alleHeaders = headers
            .plusIfMissing("kid" to keyId)
            .plusIfMissing("typ" to "JWT")
            .plusIfMissing("alg" to "RS256")

        val alleClaims = claims
            .plusIfMissing("iat" to iat)
            .plusIfMissing("nbf" to iat)
            .plusIfMissing("exp" to iat + 10)
            .plusIfMissing("jti" to "${UUID.randomUUID()}")

        val signingInput = "${alleHeaders.jwtPart}.${alleClaims.jwtPart}"
        signature.update(signingInput.toByteArray())
        val signature = base64Encoder.encodeToString(signature.sign())
        return "$signingInput.$signature"
    }

    private companion object {
        private fun Map<String, Any>.plusIfMissing(pair: Pair<String, Any>) = if (keys.contains(pair.first)) this else this.plus(pair)
        private val Map<String, Any>.jwtPart get() = base64Encoder.encodeToString(json.toByteArray())
        private fun Map<String, Any?>.hent(key: String, navn: String) = checkNotNull(get(key)?.takeIf { it is String }?.let { it as String }) { "$navn ($key) må være satt. Fant kun $keys" }
        private fun Map<String, Any?>.somRSAPrivateKey(): RSAPrivateKey {
            val keyType = hent("kty", "Key type")
            check(keyType.uppercase() == "RSA") { "Key type (kty) må være RSA" }
            val modulus = hent("n", "Modulus").decodeToBigInteger
            val privateExponent = hent("d", "Private exponent").decodeToBigInteger
            val keySpec = RSAPrivateKeySpec(modulus, privateExponent)
            val factory = KeyFactory.getInstance("RSA")
            return (factory.generatePrivate(keySpec) as RSAPrivateKey)
        }
        private val Map<String, Any>.json get() = entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) -> "\"$key\":${value.jsonValue}"}

        private val Any.jsonValue get() = when (this) {
            is Number, is Boolean -> this
            is String -> "\"$this\""
            else -> error("Støtter kun å lage jsons fra primitive verdier. Støtter ikke ${this::class.simpleName}")
        }

        private val base64Decoder = Base64.getUrlDecoder()
        private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
        private val String.decodeToBigInteger get() = BigInteger(1, base64Decoder.decode(this))
    }
}