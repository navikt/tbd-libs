package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.github.navikt.tbd_libs.result_object.Result
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
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
        val result = deserializeSoapBody<Greeting>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> error("Forventet OK resultat")
                is SoapResult.Ok -> assertEquals(expectedGreeting, svar.response.greeting)
            }
            else -> error("Forventet OK resultat")
        }
    }

    @Test
    fun `deserialiserer soap response uten header`() {
        val expectedGreeting = "Hello, World"

        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Body>
        <greeting>$expectedGreeting</greeting>
    </Soap:Body>
</Soap:Envelope> 
"""
        val result = deserializeSoapBody<Greeting>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> error("Forventet OK resultat")
                is SoapResult.Ok -> assertEquals(expectedGreeting, svar.response.greeting)
            }
            else -> error("Forventet OK resultat")
        }
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
        val result = deserializeSoapBody<ResponseLength>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> error("Forventet OK resultat")
                is SoapResult.Ok -> {
                    assertEquals("METERS", svar.response.length.unit)
                    assertEquals(1337, svar.response.length.value)
                }
            }
            else -> error("Forventet OK resultat")
        }
    }

    @Test
    fun `deserialiserer liste med ett element`() {
        val expectedGreeting = "Hello, World!"
        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Header>
        <Action xmlns="https://www.w3.org/2005/08/addressing">min action</Action>
        <MessageID xmlns="https://www.w3.org/2005/08/addressing">en message ID</MessageID>
        <RelatesTo xmlns="https://www.w3.org/2005/08/addressing">urn:uuid:d8aa0031-4ead-432f-abda-fa663ad4bc71</RelatesTo>
    </Soap:Header>
    <Soap:Body>
        <greetings>
            <greeting>$expectedGreeting</greeting>
        </greetings>
    </Soap:Body>
</Soap:Envelope> 
"""
        val result = deserializeSoapBody<Greetings>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> error("Forventet OK resultat")
                is SoapResult.Ok -> {
                    assertEquals(1, svar.response.greetings.size)
                    assertEquals(expectedGreeting, svar.response.greetings.single().greeting)
                }
            }
            else -> error("Forventet OK resultat")
        }
    }

    @Test
    fun `håndterer soap fault`() {
        val errorCode = "E2000"
        val errorMessage = "Something bad"

        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Header>
        <Action xmlns="https://www.w3.org/2005/08/addressing">min action</Action>
        <MessageID xmlns="https://www.w3.org/2005/08/addressing">en message ID</MessageID>
        <RelatesTo xmlns="https://www.w3.org/2005/08/addressing">urn:uuid:d8aa0031-4ead-432f-abda-fa663ad4bc71
        </RelatesTo>
    </Soap:Header>
    <Soap:Body>
        <Soap:Fault>
            <faultcode>$errorCode</faultcode>
            <faultstring>$errorMessage</faultstring>
        </Soap:Fault>
    </Soap:Body>
</Soap:Envelope> 
"""

        val result = deserializeSoapBody<Unit>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> {
                    assertEquals("SOAP fault: $errorCode - $errorMessage", svar.message)
                    assertNull(svar.detalje)
                }
                is SoapResult.Ok -> error("Forventet Fault resultat")
            }
            else -> error("Forventet Fault resultat")
        }
    }

    @Test
    fun `håndterer soap fault med detail`() {
        val errorCode = "E2000"
        val errorMessage = "Something bad"

        @Language("XML")
        val detalje = """<detail> 
    <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
        <errorMessage>UTBETALES-TIL-ID er ikke utfylt</errorMessage>
        <errorSource>sti til kilde</errorSource>
        <rootCause>rotårsak</rootCause>
        <dateTimeStamp>2018-01-01T23:59:59</dateTimeStamp>
</sf:simulerBeregningFeilUnderBehandling>
</detail>"""
        @Language("XML")
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
<Soap:Envelope xmlns:Soap="https://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Header>
        <Action xmlns="https://www.w3.org/2005/08/addressing">min action</Action>
        <MessageID xmlns="https://www.w3.org/2005/08/addressing">en message ID</MessageID>
        <RelatesTo xmlns="https://www.w3.org/2005/08/addressing">urn:uuid:d8aa0031-4ead-432f-abda-fa663ad4bc71
        </RelatesTo>
    </Soap:Header>
    <Soap:Body>
        <Soap:Fault>
            <faultcode>$errorCode</faultcode>
            <faultstring>$errorMessage</faultstring>
            $detalje
        </Soap:Fault>
    </Soap:Body>
</Soap:Envelope> 
"""

        val result = deserializeSoapBody<Unit>(xmlMapper, xml)
        when (result) {
            is Result.Ok -> when (val svar = result.value) {
                is SoapResult.Fault -> {
                    assertEquals("SOAP fault: $errorCode - $errorMessage", svar.message)
                    assertEquals(xmlMapper.readTree(detalje).toPrettyString(), svar.detalje)
                }
                is SoapResult.Ok -> error("Forventet Fault resultat")
            }
            else -> error("Forventet Fault resultat")
        }
    }

    @Test
    fun `håndterer ugyldig xml`() {
        val result = deserializeSoapBody<Unit>(xmlMapper, "dette er ikke xml")
        when (result) {
            is Result.Error -> {
                assertNotNull(result.cause)
                assertInstanceOf(JsonParseException::class.java, result.cause)
            }
            else -> error("Forventet InvalidResponse resultat")
        }
    }

    @Test
    fun `håndterer mangelfull soap envelope`() {
        @Language("XML")
        val xml = "<greeting>Hello</greeting>"
        val result = deserializeSoapBody<Unit>(xmlMapper, xml)
        when (result) {
            is Result.Error -> {
                assertEquals("SOAP Body er null", result.error)
                assertNull(result.cause)
            }
            else -> error("Forventet InvalidResponse resultat")
        }
    }

    private data class Greeting(
        @param:JacksonXmlProperty(localName = "greeting")
        val greeting: String
    )

    private data class Greetings(
        @param:JacksonXmlProperty(localName = "greetings")
        @param:JacksonXmlElementWrapper(useWrapping = false)
        val greetings: List<Greeting>
    )

    private data class ResponseLength(
        @param:JacksonXmlProperty(localName = "length")
        val length: Length
    )

    private data class Length(
        @param:JacksonXmlProperty(localName = "unit", isAttribute = true)
        val unit: String,
        @param:JacksonXmlProperty(localName = NAME_FOR_TEXT_ELEMENT)
        val value: Int
    )
}
