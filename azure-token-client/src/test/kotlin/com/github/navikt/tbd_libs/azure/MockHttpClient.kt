package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import java.net.http.HttpClient

class MockHttpClient {
    companion object {
        @Language("JSON")
        val okTokenResponse = """{
  "token_type": "Bearer",
  "expires_in": 3599,
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP..."
}"""

        fun mockOkResponse(): HttpClient {
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.send<String>(any(), any())
            } returns MockHttpResponse(okTokenResponse)
            return httpClient
        }

        fun verifiserPOST(httpClient: HttpClient) {
            verify {
                httpClient.send<String>(match { request ->
                    request.method().uppercase() == "POST"
                }, any())
            }
        }
        fun verifiserClientSecretRequestBody(httpClient: HttpClient, clientId: String, scope: String, clientSecret: String) {
            verifiserRequestBody(httpClient) { requestBody ->
                requestBody == "client_id=$clientId&scope=$scope&grant_type=client_credentials&client_secret=$clientSecret"
            }
        }

        fun verifiserJwtRequestBody(httpClient: HttpClient, clientId: String, scope: String, jwt: String) {
            verifiserRequestBody(httpClient) { requestBody ->
                requestBody == "client_id=$clientId&scope=$scope&grant_type=client_credentials&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=$jwt"
            }
        }

        fun verifiserOBOClientSecretRequestBody(httpClient: HttpClient, clientId: String, scope: String, otherToken: String, clientSecret: String) {
            verifiserRequestBody(httpClient) { requestBody ->
                requestBody == "client_id=$clientId&scope=$scope&grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&requested_token_use=on_behalf_of&assertion=$otherToken&client_secret=$clientSecret"
            }
        }

        fun verifiserOBOJwtRequestBody(httpClient: HttpClient, clientId: String, scope: String, otherToken: String, jwt: String) {
            verifiserRequestBody(httpClient) { requestBody ->
                requestBody == "client_id=$clientId&scope=$scope&grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&requested_token_use=on_behalf_of&assertion=$otherToken&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=$jwt"
            }
        }

        private fun verifiserRequestBody(httpClient: HttpClient, verifisering: (body: String) -> Boolean) {
            verify {
                httpClient.send<String>(match { request ->
                    verifisering(request.bodyAsString())
                }, any())
            }
        }
    }
}