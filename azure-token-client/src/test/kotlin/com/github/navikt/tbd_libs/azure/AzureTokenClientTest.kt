package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.mockOkResponse
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserJwtRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserClientSecretRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserPOST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient

class AzureTokenClientTest {
    private companion object {
        private val tokenEndpoint = URI("http://token-service/v1/token")
        private const val CLIENT_ID = "test-client-id"
        private const val SECRET_VALUE = "foobar"
        private const val SCOPE = "testscope"
    }

    @Test
    fun `fetch token from client secret`() {
        val authMethod = AzureAuthMethod.Secret(SECRET_VALUE)
        val (httpClient, result) = requestToken(authMethod)
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.token)
        verifiserPOST(httpClient)
        verifiserClientSecretRequestBody(httpClient, CLIENT_ID, SCOPE, SECRET_VALUE)
    }

    @Test
    fun `fetch token from jwt`() {
        val authMethod = AzureAuthMethod.Jwt(SECRET_VALUE)
        val (httpClient, result) = requestToken(authMethod)
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.token)
        verifiserPOST(httpClient)
        verifiserJwtRequestBody(httpClient, CLIENT_ID, SCOPE, SECRET_VALUE)
    }

    private fun requestToken(authMethod: AzureAuthMethod): Pair<HttpClient, AzureToken> {
        val httpClient = mockOkResponse()
        val client = AzureTokenClient(tokenEndpoint, CLIENT_ID, authMethod, httpClient)
        return httpClient to client.bearerToken(SCOPE)
    }
}