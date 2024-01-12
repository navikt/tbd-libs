package com.github.navikt.tbd_libs.soap

import org.intellij.lang.annotations.Language
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

class MinimalSoapClient(
    private val serviceUrl: URI,
    private val tokenProvider: SamlTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) {

    fun doSoapAction(action: String, body: String, tokenStrategy: SoapAssertionStrategy): String {
        val requestBody = createXmlRequest(tokenStrategy.token(tokenProvider), action, body)
        val request = HttpRequest.newBuilder()
            .uri(serviceUrl)
            .header("SOAPAction", action)
            .POST(BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(request, BodyHandlers.ofString())
        return response.body() ?: throw SoapClientException("Tom responskropp fra tjenesten")
    }

    private fun createXmlRequest(assertion: String, action: String, body: String, messageId: UUID = UUID.randomUUID()): String {
        return defaultXmlBody
            .replace("{{action}}", action)
            .replace("{{messageId}}", "$messageId")
            .replace("{{assertion}}", assertion)
            .replace("{{body}}", body)
    }

    private companion object {
        @Language("XML")
        private val defaultXmlBody = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">{{action}}</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:{{messageId}}</MessageID>
        <To xmlns="http://www.w3.org/2005/08/addressing">{{serviceUrl}}</To>
        <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
        </ReplyTo>
        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                       xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
                       soap:mustUnderstand="1">
            {{assertion}}
        </wsse:Security>
    </soap:Header>
    <soap:Body>
        {{body}}
    </soap:Body>
</soap:Envelope>"""
    }
}


class SoapClientException(override val message: String, override val cause: Throwable? = null) : RuntimeException()