package com.github.navikt.tbd_libs.azure

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
}