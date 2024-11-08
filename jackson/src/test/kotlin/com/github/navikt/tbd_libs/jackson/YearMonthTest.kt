package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlin.test.Test
import kotlin.test.assertEquals

internal class YearMonthTest {

    @Test
    fun `Successfully parse YearMonth`() {
        val expected = YearMonth.of(2024, 1)
        val objectNode = jsonNode("""{"yearMonth": "$expected"}""")
        assertEquals(expected, objectNode.get("yearMonth").asYearMonth())
        assertEquals(expected, objectNode.path("yearMonth").asYearMonth())
    }

    @Test
    fun `Cannot parse YearMonth if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<NullPointerException> {
            objectNode.get("yearMonth").asYearMonth()
        }
        assertThrows<IllegalArgumentException> {
            objectNode.path("yearMonth").asYearMonth()
        }
    }

    @Test
    fun `Cannot parse YearMonth if the value of the field is not a valid yearMonth`() {
        val objectNode = jsonNode("""{"yearMonth": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("yearMonth").asYearMonth()
            objectNode.path("yearMonth").asYearMonth()
        }
    }

    @Test
    fun `Cannot parse YearMonth if the value of the field is null`() {
        val objectNode = jsonNode("""{"yearMonth": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("yearMonth").asYearMonth()
            objectNode.path("yearMonth").asYearMonth()
        }
    }

    @Test
    fun `Successfully parse YearMonth with the OrNull variant`() {
        val expected = YearMonth.of(2024, 1)
        val objectNode = jsonNode("""{"yearMonth": "$expected"}""")
        assertEquals(expected, objectNode.get("yearMonth").asYearMonthOrNull())
        assertEquals(expected, objectNode.path("yearMonth").asYearMonthOrNull())
    }

    @Test
    fun `Successfully parse YearMonth and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"yearMonth": null}""")
        assertEquals(null, objectNode.get("yearMonth").asYearMonthOrNull())
        assertEquals(null, objectNode.path("yearMonth").asYearMonthOrNull())
    }

    @Test
    fun `Successfully parse YearMonth and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.get("yearMonth").asYearMonthOrNull())
        assertEquals(null, objectNode.path("yearMonth").asYearMonthOrNull())
    }

    @Test
    fun `Cannot parse YearMonth if the value of the field is not a valid yearMonth with the OrNull variant`() {
        val objectNode = jsonNode("""{"yearMonth": "foo"}""")
        assertThrows<DateTimeParseException> {
            objectNode.get("yearMonth").asYearMonthOrNull()
            objectNode.path("yearMonth").asYearMonthOrNull()
        }
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonObjectMapper().readTree(json)
    }
}
