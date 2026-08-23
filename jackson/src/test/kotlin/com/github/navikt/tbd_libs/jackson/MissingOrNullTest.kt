package com.github.navikt.tbd_libs.jackson

import kotlin.test.Test
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import tools.jackson.databind.JsonNode
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder

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
        return jacksonMapperBuilder()
            .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
            .build()
            .readTree(json)
    }
}
