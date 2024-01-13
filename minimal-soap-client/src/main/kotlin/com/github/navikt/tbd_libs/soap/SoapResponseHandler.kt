package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.treeToValue
import kotlin.reflect.KClass
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

class SoapResponseHandler(private val mapper: ObjectMapper) {

    fun <T: Any> deserializeSoapBody(body: String, clazz: KClass<T>) =
        deserializeSoapResponse(body, clazz).body

    private fun <T: Any> deserializeSoapResponse(body: String, clazz: KClass<T>): SoapResponse<T> {
        try {
            val rootNode = mapper.readTree(body)
            return SoapResponse(deserializeSoapHeader(rootNode), deserializeSoapBody(rootNode, clazz))
        } catch (err: Exception) {
            throw SoapResponseHandlerException("Kunne ikke oversette resultatet: ${err.message}", err)
        }
    }

    private fun deserializeSoapHeader(rootNode: JsonNode): SoapHeader {
        val headerNode = rootNode.at("/Header")
        return mapper.treeToValue<SoapHeader>(headerNode)
    }

    private fun <T: Any> deserializeSoapBody(rootNode: JsonNode, clazz: KClass<T>): T {
        val bodyNode = rootNode.at("/Body")
        return mapper.treeToValue(bodyNode, clazz.java)
    }

    data class SoapHeader(
        @JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
        val action: String,
        @JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
        val messageId: String,
        @JacksonXmlProperty(localName = "RelatesTo", namespace = "http://www.w3.org/2005/08/addressing")
        val relatesTo: String
    )

    data class SoapResponse<T>(
        val header: SoapHeader,
        val body: T
    )
}

class SoapResponseHandlerException(override val message: String, override val cause: Throwable? = null) : RuntimeException()
