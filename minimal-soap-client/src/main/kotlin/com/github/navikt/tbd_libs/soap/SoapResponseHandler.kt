package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue

inline fun <reified T> deserializeSoapBody(mapper: ObjectMapper, body: String): T {
    try {
        return mapper.readValue<SoapResponse<T>>(body).body
    } catch (err: Exception) {
        throw SoapResponseHandlerException("Kunne ikke oversette resultatet: ${err.message}", err)
    }
}

data class SoapHeader(
    @JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
    @JacksonXmlProperty(localName = "RelatesTo", namespace = "http://www.w3.org/2005/08/addressing")
    val relatesTo: String
)

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @JacksonXmlProperty(localName = "Header")
    val header: SoapHeader,
    @JacksonXmlProperty(localName = "Body")
    val body: T
)

class SoapResponseHandlerException(override val message: String, override val cause: Throwable? = null) : RuntimeException()
