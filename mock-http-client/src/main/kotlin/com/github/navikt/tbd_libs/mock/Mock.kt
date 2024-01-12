package com.github.navikt.tbd_libs.mock

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

fun HttpRequest.bodyAsString(): String {
    return bodyPublisher().get().let {
        val subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)
        it.subscribe(StringSubscriber(subscriber))
        subscriber.body.toCompletableFuture().get()
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

class MockHttpResponse(
    private val body: String,
    private val statusCode: Int? = null
) : HttpResponse<String> {
    override fun body() = body

    override fun statusCode() = statusCode ?: throw NotImplementedError("Ikke implementert i mocken")

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