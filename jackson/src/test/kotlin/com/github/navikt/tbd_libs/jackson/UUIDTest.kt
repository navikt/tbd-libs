package com.github.navikt.tbd_libs.jackson

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder

internal class UUIDTest {

    @Test
    fun `Successfully parse UUID`() {
        val expected = UUID.randomUUID()
        val objectNode = jsonNode("""{"uuid": "$expected"}""")
        assertEquals(expected, objectNode.get("uuid").asUUID())
        assertEquals(expected, objectNode.path("uuid").asUUID())
    }

    @Test
    fun `Cannot parse UUID if the field is missing`() {
        val objectNode = jsonNode("""{}""")
        assertThrows<NullPointerException> {
            objectNode.get("uuid").asUUID()
        }
        assertThrows<IllegalArgumentException> {
            objectNode.path("uuid").asUUID()
        }
    }

    @Test
    fun `Cannot parse UUID if the value of the field is not a valid uuid`() {
        val objectNode = jsonNode("""{"uuid": "foo"}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("uuid").asUUID()
            objectNode.path("uuid").asUUID()
        }
    }

    @Test
    fun `Cannot parse UUID if the value of the field is null`() {
        val objectNode = jsonNode("""{"uuid": null}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("uuid").asUUID()
            objectNode.path("uuid").asUUID()
        }
    }

    @Test
    fun `Successfully parse UUID with the OrNull variant`() {
        val expected = UUID.randomUUID()
        val objectNode = jsonNode("""{"uuid": "$expected"}""")
        assertEquals(expected, objectNode.get("uuid").asUUIDOrNull())
        assertEquals(expected, objectNode.path("uuid").asUUIDOrNull())
    }

    @Test
    fun `Successfully parse UUID and get null back if the value is null with the OrNull variant`() {
        val objectNode = jsonNode("""{"uuid": null}""")
        assertEquals(null, objectNode.get("uuid").asUUIDOrNull())
        assertEquals(null, objectNode.path("uuid").asUUIDOrNull())
    }

    @Test
    fun `Successfully parse UUID and get null back if the field is missing with the OrNull variant`() {
        val objectNode = jsonNode("""{}""")
        assertEquals(null, objectNode.get("uuid").asUUIDOrNull())
        assertEquals(null, objectNode.path("uuid").asUUIDOrNull())
    }

    @Test
    fun `Cannot parse UUID if the value of the field is not a valid uuid with the OrNull variant`() {
        val objectNode = jsonNode("""{"uuid": "foo"}""")
        assertThrows<IllegalArgumentException> {
            objectNode.get("uuid").asUUIDOrNull()
            objectNode.path("uuid").asUUIDOrNull()
        }
    }

    private fun jsonNode(@Language("JSON") json: String): JsonNode {
        return jacksonMapperBuilder()
            .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
            .build()
            .readTree(json)
    }
}
