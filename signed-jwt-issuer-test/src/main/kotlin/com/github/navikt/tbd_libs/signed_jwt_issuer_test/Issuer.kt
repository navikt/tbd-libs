package com.github.navikt.tbd_libs.signed_jwt_issuer_test

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import org.intellij.lang.annotations.Language
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64

class Issuer(val navn: String, val audience: String) {
    private val privateKey: RSAPrivateKey
    private val publicKey: RSAPublicKey
    private val algorithm: Algorithm
    private val port = ServerSocket(0).use { it.localPort }
    private val wireMockServer = WireMockServer(port)

    private val baseUrl = "http://localhost:$port"
    private val jwksUri = URI("$baseUrl/jwks")
    private val wellKnownUri = URI("$baseUrl/.well-known")

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(512)

        val keyPair = keyPairGenerator.genKeyPair()
        privateKey = keyPair.private as RSAPrivateKey
        publicKey = keyPair.public as RSAPublicKey
        algorithm = Algorithm.RSA256(publicKey, privateKey)
    }

    @Language("JSON")
    private fun jwks() = """
    {
       "keys": [
           {
               "kty": "RSA",
               "alg": "RS256",
               "kid": "key-1234",
               "e": "${Base64.getUrlEncoder().encodeToString(publicKey.publicExponent.toByteArray())}",
               "n": "${Base64.getUrlEncoder().encodeToString(publicKey.modulus.toByteArray())}"
           }
       ]
    }
    """

    @Language("JSON")
    private fun wellKnown() = """
    {
        "jwks_uri": "$jwksUri", 
        "issuer": "$navn"
    }
    """

    fun accessToken(builder: JWTCreator.Builder.() -> Unit = {}) = JWT.create()
        .withIssuer(navn)
        .withAudience(audience)
        .withKeyId("key-1234")
        .withIssuedAt(Instant.now())
        .withExpiresAt(Instant.now().plusSeconds(3600))
        .apply { builder() }
        .sign(algorithm)

    fun jwksUri() = jwksUri

    fun wellKnownUri() = wellKnownUri

    fun start() = apply {
        wireMockServer.start()
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/jwks")).willReturn(WireMock.okJson(jwks())))
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/.well-known")).willReturn(WireMock.okJson(wellKnown())))
    }

    fun startet() = kotlin.runCatching { Socket("localhost", port).use { it.isConnected } }.isSuccess

    fun stop() = apply {
        wireMockServer.stop()
    }
}
