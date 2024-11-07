package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class MissingOrNullTest {

    @Test
    fun missing() {
        val node = jsonNode("""{}""")
        assertTrue(node.get("foo").isMissingOrNull())
        assertTrue(node.path("foo").isMissingOrNull())
    }

    @Test
    fun `null`() {
        val node = jsonNode("""{"foo": null}""")
        assertTrue(node.get("foo").isMissingOrNull())
        assertTrue(node.path("foo").isMissingOrNull())
    }

    @Test
    fun `not missing or null`() {
        val node = jsonNode("""{"foo": "some data"}""")
        assertFalse(node.get("foo").isMissingOrNull())
        assertFalse(node.path("foo").isMissingOrNull())
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonObjectMapper().readTree(json)
    }
}
