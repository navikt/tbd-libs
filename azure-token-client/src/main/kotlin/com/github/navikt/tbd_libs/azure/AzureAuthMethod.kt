package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.signedjwt.SignedJwt
import java.net.URI

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

    class Jwk constructor(jwk: Map<String, Any?>, clientId: String, tokenEndpoint: URI): AzureAuthMethod {
        private val signedJwt = SignedJwt(jwk)
        private val claims = mapOf("aud" to "$tokenEndpoint", "sub" to clientId, "iss" to clientId)
        override fun requestParameters() = Jwt(signedJwt.generate(claims = claims)).requestParameters()
    }
}