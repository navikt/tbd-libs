package com.github.navikt.tbd_libs.mock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

class MockTest {

    @Test
    fun `kan mocke response`() {
        val expectedRequestBody = "Hello, Server!"
        val expectedResponseBody = "Hello, World!"
        val callId = "${UUID.randomUUID()}"
        val expectedStatusCode = 201

        val actualRequest = HttpRequest
            .newBuilder()
            .uri(URI("http://localhost"))
            .POST(BodyPublishers.ofString(expectedRequestBody))

        val httpClient = mockk<HttpClient>()
        every {
            httpClient.send<String>(any(), any())
        } returns MockHttpResponse(expectedResponseBody, expectedStatusCode, headers = mapOf("X-Call-Id" to callId))

        val result = httpClient.send(actualRequest.build(), BodyHandlers.ofString())

        verify { httpClient.send<String>(match {
            it.bodyAsString() == expectedRequestBody
        }, any()) }

        assertEquals(expectedStatusCode, result.statusCode())
        assertEquals(callId, result.headers().firstValue("X-Call-Id").get())
        assertEquals(expectedResponseBody, result.body())
    }
}