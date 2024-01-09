package com.github.navikt.tbd_libs.azure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Flow
import javax.net.ssl.SSLSession

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
            } returns TestResponse(okTokenResponse)
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

        fun verifiserJwtRequestBody(httpClient: HttpClient, clientId: String, scope: String, certificate: String) {
            verifiserRequestBody(httpClient) { requestBody ->
                requestBody == "client_id=$clientId&scope=$scope&grant_type=client_credentials&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=$certificate"
            }
        }

        private fun verifiserRequestBody(httpClient: HttpClient, verifisering: (body: String) -> Boolean) {
            verify {
                httpClient.send<String>(match { request ->
                    verifisering(request.bodyAsString())
                }, any())
            }
        }

        private fun HttpRequest.bodyAsString(): String {
            return bodyPublisher().get().let {
                val subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)
                it.subscribe(StringSubscriber(subscriber))
                subscriber.body.toCompletableFuture().get()
            }
        }
    }

    private class StringSubscriber(private val other: HttpResponse.BodySubscriber<String>) : Flow.Subscriber<ByteBuffer> {
        override fun onSubscribe(subscription: Flow.Subscription) {
            other.onSubscribe(subscription)
        }

        override fun onNext(item: ByteBuffer) {
            other.onNext(listOf(item))
        }

        override fun onError(throwable: Throwable) {
            other.onError(throwable)
        }

        override fun onComplete() {
            other.onComplete()
        }
    }

    private class TestResponse(private val body: String) : HttpResponse<String> {
        override fun body() = body

        override fun statusCode(): Int {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun request(): HttpRequest {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun previousResponse(): Optional<HttpResponse<String>> {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun headers(): HttpHeaders {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun sslSession(): Optional<SSLSession> {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun uri(): URI {
            throw NotImplementedError("Ikke implementert i mocken")
        }

        override fun version(): HttpClient.Version {
            throw NotImplementedError("Ikke implementert i mocken")
        }
    }
}