package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.mockOkResponse
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserJwtRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserClientSecretRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserOBOClientSecretRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserOBOJwtRequestBody
import com.github.navikt.tbd_libs.azure.MockHttpClient.Companion.verifiserPOST
import com.github.navikt.tbd_libs.result_object.Result
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
        private const val OTHER_TOKEN = "eyJ0eXAiOiJKV1QiLCJub25jZSI6IkFRQUJBQUFBQUFCbm..."
    }

    @Test
    fun `fetch token from client secret`() {
        val authMethod = AzureAuthMethod.Secret(SECRET_VALUE)
        val (httpClient, result) = requestToken(authMethod)
        result as Result.Ok
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.value.token)
        verifiserPOST(httpClient)
        verifiserClientSecretRequestBody(httpClient, CLIENT_ID, SCOPE, SECRET_VALUE)
    }

    @Test
    fun `fetch token from jwt`() {
        val authMethod = AzureAuthMethod.Jwt(SECRET_VALUE)
        val (httpClient, result) = requestToken(authMethod)
        result as Result.Ok
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.value.token)
        verifiserPOST(httpClient)
        verifiserJwtRequestBody(httpClient, CLIENT_ID, SCOPE, SECRET_VALUE)
    }

    @Test
    fun `fetch obo token from client secret`() {
        val authMethod = AzureAuthMethod.Secret(SECRET_VALUE)
        val (httpClient, result) = requestOboToken(authMethod)
        result as Result.Ok
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.value.token)
        verifiserPOST(httpClient)
        verifiserOBOClientSecretRequestBody(httpClient, CLIENT_ID, SCOPE, OTHER_TOKEN, SECRET_VALUE)
    }

    @Test
    fun `fetch obo token from jwt`() {
        val authMethod = AzureAuthMethod.Jwt(SECRET_VALUE)
        val (httpClient, result) = requestOboToken(authMethod)
        result as Result.Ok
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP...", result.value.token)
        verifiserPOST(httpClient)
        verifiserOBOJwtRequestBody(httpClient, CLIENT_ID, SCOPE, OTHER_TOKEN, SECRET_VALUE)
    }

    private fun requestToken(authMethod: AzureAuthMethod): Pair<HttpClient, Result<AzureToken>> {
        val httpClient = mockOkResponse()
        val client = AzureTokenClient(tokenEndpoint, CLIENT_ID, authMethod, httpClient)
        return httpClient to client.bearerToken(SCOPE)
    }

    private fun requestOboToken(authMethod: AzureAuthMethod): Pair<HttpClient, Result<AzureToken>> {
        val httpClient = mockOkResponse()
        val client = AzureTokenClient(tokenEndpoint, CLIENT_ID, authMethod, httpClient)
        return httpClient to client.onBehalfOfToken(SCOPE, OTHER_TOKEN)
    }
}