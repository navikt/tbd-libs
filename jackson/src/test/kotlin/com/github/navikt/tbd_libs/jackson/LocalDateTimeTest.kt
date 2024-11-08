package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LocalDateTimeTest {

    @Test
    fun `Successfully parse LocalDateTime`() {
        val expected = LocalDate.of(2024, 1, 1).atStartOfDay()
        val objectNode = jsonNode("""{"timestamp": "$expected"}""")
        assertEquals(expected, objectNode.get("timestamp").asLocalDateTime())
        assertEquals(expected, objectNode.path("timestamp").asLocalDateTime())
    }

    @Test
    fun `Cannot parse LocalDateTime if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<NullPointerException> {
            objectNode.get("timestamp").asLocalDateTime()
        }
        assertThrows<IllegalArgumentException> {
            objectNode.path("timestamp").asLocalDateTime()
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid timestamp`() {
        val objectNode = jsonNode("""{"timestamp": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("timestamp").asLocalDateTime()
            objectNode.path("timestamp").asLocalDateTime()
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is null`() {
        val objectNode = jsonNode("""{"timestamp": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("timestamp").asLocalDateTime()
            objectNode.path("timestamp").asLocalDateTime()
        }
    }

    @Test
    fun `Successfully parse LocalDateTime with the OrNull variant`() {
        val expected = LocalDate.of(2024, 1, 1).atStartOfDay()
        val objectNode = jsonNode("""{"timestamp": "$expected"}""")
        assertEquals(expected, objectNode.get("timestamp").asLocalDateTimeOrNull())
        assertEquals(expected, objectNode.path("timestamp").asLocalDateTimeOrNull())
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"timestamp": null}""")
        assertEquals(null, objectNode.get("timestamp").asLocalDateTimeOrNull())
        assertEquals(null, objectNode.path("timestamp").asLocalDateTimeOrNull())
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.get("timestamp").asLocalDateTimeOrNull())
        assertEquals(null, objectNode.path("timestamp").asLocalDateTimeOrNull())
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid timestamp with the OrNull variant`() {
        val objectNode = jsonNode("""{"timestamp": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("timestamp").asLocalDateTimeOrNull()
            objectNode.path("timestamp").asLocalDateTimeOrNull()
        }
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonObjectMapper().readTree(json)
    }
}
