package com.github.navikt.tbd_libs.jackson

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LocalDateTest {
    @Test
    fun `Successfully parse LocalDate`() {
        val expected = LocalDate.of(2024, 1, 1)
        val objectNode = jsonNode("""{"date": "$expected"}""")
        assertEquals(expected, objectNode.get("date").asLocalDate())
        assertEquals(expected, objectNode.path("date").asLocalDate())
    }

    @Test
    fun `Cannot parse LocalDate if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<NullPointerException> {
            objectNode.get("date").asLocalDate()
        }
        assertThrows<IllegalArgumentException> {
            objectNode.path("date").asLocalDate()
        }
    }

    @Test
    fun `Cannot parse LocalDate if the value of the field is not a valid date`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("date").asLocalDate()
            objectNode.path("date").asLocalDate()
        }
    }

    @Test
    fun `Cannot parse LocalDate if the value of the field is null`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("date").asLocalDate()
            objectNode.path("date").asLocalDate()
        }
    }

    @Test
    fun `Successfully parse LocalDate with the OrNull variant`() {
        val expected = LocalDate.of(2024, 1, 1)
        val objectNode = jsonNode("""{"date": "$expected"}""")
        assertEquals(expected, objectNode.get("date").asLocalDateOrNull())
        assertEquals(expected, objectNode.path("date").asLocalDateOrNull())
    }

    @Test
    fun `Successfully parse LocalDate and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": null}""")
        assertEquals(null, objectNode.get("date").asLocalDateOrNull())
        assertEquals(null, objectNode.path("date").asLocalDateOrNull())
    }

    @Test
    fun `Successfully parse LocalDate and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.get("date").asLocalDateOrNull())
        assertEquals(null, objectNode.path("date").asLocalDateOrNull())
    }

    @Test
    fun `Cannot parse LocalDate if the value of the field is not a valid date with the OrNull variant`() {
        val objectNode = jsonNode("""{"date": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("date").asLocalDateOrNull()
            objectNode.path("date").asLocalDateOrNull()
        }
    }

    private fun jsonNode(
        @Language("JSON") json: String,
    ): JsonNode =
        jacksonMapperBuilder()
            .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
            .build()
            .readTree(json)
}
