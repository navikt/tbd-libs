package com.github.navikt.tbd_libs.rapids_and_rivers

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class RiverTest {

    @Test
    internal fun `sets id if missing`() {
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertTrue(gotMessage)
        assertDoesNotThrow { gotPacket.id.toUUID() }
    }

    @Test
    internal fun `sets custom id if missing`() {
        val expected = "notSoRandom"
        river = configureRiver(River(rapid) { expected })
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertTrue(gotMessage)
        assertEquals(expected, gotPacket.id)
    }

    @Test
    internal fun `invalid json`() {
        river.onMessage("invalid json", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertFalse(gotMessage)
        assertTrue(messageProblems.hasErrors())
    }

    @Test
    internal fun `no validations`() {
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertTrue(gotMessage)
        assertFalse(messageProblems.hasErrors())
    }

    @Test
    internal fun `failed validations`() {
        river.validate { it.requireKey("key") }
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertFalse(gotMessage)
        assertTrue(messageProblems.hasErrors())
    }

    @Test
    internal fun `passing validations`() {
        river.validate { it.requireValue("hello", "world") }
        river.onMessage("{\"hello\": \"world\"}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertTrue(gotMessage)
        assertFalse(messageProblems.hasErrors())
    }

    private val context = object : MessageContext {
        override fun publish(message: String) {}
        override fun publish(key: String, message: String) {}
        override fun rapidName(): String {return "test"}
    }

    private var gotMessage = false
    private lateinit var gotPacket: JsonMessage
    private lateinit var messageProblems: MessageProblems
    private lateinit var river: River
    private val rapid = object : RapidsConnection() {
        override fun publish(message: String) {}

        override fun publish(key: String, message: String) {}
        override fun rapidName(): String {
            return "test"
        }

        override fun start() {}

        override fun stop() {}
    }

    @BeforeEach
    internal fun setup() {
        messageProblems = MessageProblems("{}")
        river = configureRiver(River(rapid))
    }

    private fun configureRiver(river: River): River =
        river.register(object : River.PacketListener {
            override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
                gotPacket = packet
                gotMessage = true
            }

            override fun onSevere(
                error: MessageProblems.MessageException,
                context: MessageContext
            ) {
                messageProblems = error.problems
            }

            override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
                messageProblems = problems
            }
        })
}
