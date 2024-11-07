package com.github.navikt.tbd_libs.jackson_helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDateTimeTest {

    @Test
    fun `Successfully parse LocalDateTime`() {
        val objectNode = jsonNode("""{"date": "2024-01-01T00:00:00.000"}""")
        assertEquals(LocalDate.of(2024, 1, 1).atStartOfDay(), objectNode.asLocalDateTime("date"))
    }

    @Test
    fun `Cannot parse LocalDateTime if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<IllegalArgumentException> {
            objectNode.asLocalDateTime("date")
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid date`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.asLocalDateTime("date")
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is null`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertThrows<DateTimeParseException> {
            objectNode.asLocalDateTime("date")
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the function is called on something other than an ObjectNode`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.path("date").asLocalDateTime("date")
        }
    }

    @Test
    fun `Successfully parse LocalDateTime with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": "2024-01-01T00:00:00.000"}""")
        assertEquals(LocalDate.of(2024, 1, 1).atStartOfDay(), objectNode.asLocalDateTimeOrNull("date"))
        assertEquals(LocalDate.of(2024, 1, 1).atStartOfDay(), objectNode.asLocalDateTimeOrNull("date"))
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertEquals(null, objectNode.asLocalDateTimeOrNull("date"))
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.asLocalDateTimeOrNull("date"))
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid date with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.asLocalDateTimeOrNull("date")
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the function is called on something other than an ObjectNode with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.path("date").asLocalDateTimeOrNull("date")
        }
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonObjectMapper().readTree(json)
    }
}
