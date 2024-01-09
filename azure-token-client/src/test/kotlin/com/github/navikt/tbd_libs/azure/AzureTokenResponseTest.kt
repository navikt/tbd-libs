package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime

class AzureTokenResponseTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun deserializeTokenResponse() {
        @Language("JSON")
        val json = """{
  "token_type": "Bearer",
  "expires_in": 3599,
  "ext_expires_in": 3599,
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik1uQ19WWmNBVGZNNXBP..."
}"""
        val utstedtTidspunkt = LocalDateTime.of(2018, 1, 1, 13, 37, 0, 123)
        val reader = objectMapper.reader(InjectableValues.Std()
            .addValue(LocalDateTime::class.java, utstedtTidspunkt)
        ).forType(AzureTokenResponse::class.java)

        val token = assertDoesNotThrow { reader.readValue<AzureTokenResponse>(json) }
        assertEquals(utstedtTidspunkt.plusSeconds(3599), token.expirationTime)
    }

    @Test
    fun deserializeErrorResponse() {
        @Language("JSON")
        val json = """{
  "error": "invalid_scope",
  "error_description": "AADSTS70011: The provided value for the input parameter 'scope' is not valid. The scope https://foo.microsoft.com/.default is not valid.\r\nTrace ID: 255d1aef-8c98-452f-ac51-23d051240864\r\nCorrelation ID: fb3d2015-bc17-4bb9-bb85-30c5cf1aaaa7\r\nTimestamp: 2016-01-09 02:02:12Z",
  "error_codes": [
    70011
  ],
  "timestamp": "YYYY-MM-DD HH:MM:SSZ",
  "trace_id": "255d1aef-8c98-452f-ac51-23d051240864",
  "correlation_id": "fb3d2015-bc17-4bb9-bb85-30c5cf1aaaa7"
}"""
        assertDoesNotThrow { objectMapper.readValue<AzureErrorResponse>(json) }
    }
}