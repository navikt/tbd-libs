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
        val objectNode = jsonNode("""{"date": "$expected"}""")
        assertEquals(expected, objectNode.get("date").asLocalDateTime())
        assertEquals(expected, objectNode.path("date").asLocalDateTime())
    }

    @Test
    fun `Cannot parse LocalDateTime if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<NullPointerException> {
            objectNode.get("date").asLocalDateTime()
        }
        assertThrows<IllegalArgumentException> {
            objectNode.path("date").asLocalDateTime()
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid date`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("date").asLocalDateTime()
            objectNode.path("date").asLocalDateTime()
        }
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is null`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("date").asLocalDateTime()
            objectNode.path("date").asLocalDateTime()
        }
    }

    @Test
    fun `Successfully parse LocalDateTime with the OrNull variant`() {
        val expected = LocalDate.of(2024, 1, 1).atStartOfDay()
        val objectNode = jsonNode("""{"date": "$expected"}""")
        assertEquals(expected, objectNode.get("date").asLocalDateTimeOrNull())
        assertEquals(expected, objectNode.path("date").asLocalDateTimeOrNull())
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertEquals(null, objectNode.get("date").asLocalDateTimeOrNull())
        assertEquals(null, objectNode.path("date").asLocalDateTimeOrNull())
    }

    @Test
    fun `Successfully parse LocalDateTime and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.get("date").asLocalDateTimeOrNull())
        assertEquals(null, objectNode.path("date").asLocalDateTimeOrNull())
    }

    @Test
    fun `Cannot parse LocalDateTime if the value of the field is not a valid date with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("date").asLocalDateTimeOrNull()
            objectNode.path("date").asLocalDateTimeOrNull()
        }
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonObjectMapper().readTree(json)
    }
}
