package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue

inline fun <reified T> deserializeSoapBody(mapper: ObjectMapper, body: String): SoapResult<T> {
    val fault = try {
        mapper.readValue<SoapResponse<SoapFault>>(body).body.fault
    } catch (_: Exception) { null }
    return when (fault) {
        null -> try {
            when (val ting = mapper.readValue<SoapResponse<T?>>(body).body) {
                null -> SoapResult.InvalidResponse("Body er null", null)
                else -> SoapResult.Ok(ting)
            }
        } catch (err: Exception) {
            SoapResult.InvalidResponse(body, err)
        }
        else -> SoapResult.Fault("SOAP fault: ${fault.code} - ${fault.messsage}", fault.detail?.toPrettyString())
    }
}

sealed interface SoapResult<T> {
    data class Ok<T>(val response: T) : SoapResult<T>
    data class Fault<T>( val message: String, val detalje: String?) : SoapResult<T>
    data class InvalidResponse<T>(val responseBody: String, val exception: Throwable?) : SoapResult<T>
}

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @JacksonXmlProperty(localName = "Header")
    val header: SoapHeader?,
    @JacksonXmlProperty(localName = "Body")
    val body: T
)

data class SoapHeader(
    @JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
    @JacksonXmlProperty(localName = "RelatesTo", namespace = "http://www.w3.org/2005/08/addressing")
    val relatesTo: String
)

data class SoapFault(
    @JacksonXmlProperty(localName = "Fault", namespace = "http://www.w3.org/2003/05/soap-envelope")
    val fault: Fault
)

data class Fault(
    @JacksonXmlProperty(localName = "faultcode")
    val code: String,
    @JacksonXmlProperty(localName = "faultstring")
    val messsage: String,
    @JacksonXmlProperty(localName = "detail")
    val detail: JsonNode?
)