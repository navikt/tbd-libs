package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SoapResponseHandlerTest {
    private companion object {
        private const val NAME_FOR_TEXT_ELEMENT = "innerText"
        private val xmlMapper = XmlMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // issue: https://github.com/FasterXML/jackson-module-kotlin/issues/138
            // workaround: https://github.com/FasterXML/jackson-module-kotlin/issues/138#issuecomment-576484905
            .nameForTextElement(NAME_FOR_TEXT_ELEMENT)
            .build()
        private val handler = SoapResponseHandler(xmlMapper)
    }

    @Test
    fun `deserialiserer soap response`() {
        val expectedGreeting = "Hello, World"

        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Header>
        <Action xmlns="https://www.w3.org/2005/08/addressing">min action</Action>
        <MessageID xmlns="https://www.w3.org/2005/08/addressing">en message ID</MessageID>
        <RelatesTo xmlns="https://www.w3.org/2005/08/addressing">urn:uuid:d8aa0031-4ead-432f-abda-fa663ad4bc71</RelatesTo>
    </Soap:Header>
    <Soap:Body>
        <greeting>$expectedGreeting</greeting>
    </Soap:Body>
</Soap:Envelope> 
"""
        val result = handler.deserializeSoapBody(xml, Response::class)
        assertEquals(expectedGreeting, result.greeting)
    }
    @Test
    fun `deserialiserer verdi med attributt`() {
        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Header>
        <Action xmlns="https://www.w3.org/2005/08/addressing">min action</Action>
        <MessageID xmlns="https://www.w3.org/2005/08/addressing">en message ID</MessageID>
        <RelatesTo xmlns="https://www.w3.org/2005/08/addressing">urn:uuid:d8aa0031-4ead-432f-abda-fa663ad4bc71</RelatesTo>
    </Soap:Header>
    <Soap:Body>
        <length unit="METERS">1337</length>
    </Soap:Body>
</Soap:Envelope> 
"""
        val result = handler.deserializeSoapBody(xml, ResponseLength::class)
        assertEquals("METERS", result.length.unit)
        assertEquals(1337, result.length.value)
    }

    @Test
    fun `håndterer ugyldig xml`() {
        assertThrows<SoapResponseHandlerException> {
            handler.deserializeSoapBody("dette er ikke xml", Response::class)
        }
    }

    @Test
    fun `håndterer mangelfull soap envelope`() {
        @Language("XML")
        val xml = "<greeting>Hello</greeting>"
        assertThrows<SoapResponseHandlerException> {
            handler.deserializeSoapBody(xml, Response::class)
        }
    }

    private data class Response(
        @JacksonXmlProperty(localName = "greeting")
        val greeting: String
    )

    private data class ResponseLength(
        @JacksonXmlProperty(localName = "length")
        val length: Length
    )

    private data class Length(
        @JacksonXmlProperty(localName = "unit", isAttribute = true)
        val unit: String,
        @JacksonXmlProperty(localName = NAME_FOR_TEXT_ELEMENT)
        val value: Int
    )
}