package com.github.navikt.tbd_libs.rapids_and_rivers

import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.intellij.lang.annotations.Language
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
    internal fun `failed preconditions`() {
        river.precondition { it.requireValue("@event_name", "tick") }
        river.validate { error("this should not be called") }
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertFalse(gotMessage)
        assertTrue(messageProblems.hasErrors())
        assertEquals(RiverValidationResult.PRECONDITION_FAILED, validationResult)
    }

    @Test
    internal fun `failed validations`() {
        river.validate { it.requireKey("key") }
        river.onMessage("{}", context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertFalse(gotMessage)
        assertTrue(messageProblems.hasErrors())
        assertEquals(RiverValidationResult.VALIDATION_FAILED, validationResult)
    }

    @Test
    internal fun `passing validations`() {
        river.precondition { it.requireValue("@event_name", "greeting") }
        river.validate { it.requireValue("hello", "world") }
        @Language("JSON")
        val message = """{ "@event_name": "greeting", "hello": "world" }"""
        river.onMessage(message, context, MessageMetadata("", -1, -1, null, emptyMap()), SimpleMeterRegistry())
        assertTrue(gotMessage)
        assertFalse(messageProblems.hasErrors())
        assertEquals(RiverValidationResult.PASSED, validationResult)
    }

    private val context = object : MessageContext {
        override fun publish(message: String) {}
        override fun publish(key: String, message: String) {}
        override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
            return emptyList<SentMessage>() to emptyList()
        }
        override fun rapidName(): String {return "test"}
    }

    private var gotMessage = false
    private lateinit var gotPacket: JsonMessage
    private lateinit var messageProblems: MessageProblems
    private lateinit var river: River
    private lateinit var validationResult: RiverValidationResult
    private val rapid = object : RapidsConnection() {
        override fun publish(message: String) {}
        override fun publish(key: String, message: String) {}
        override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
            return emptyList<SentMessage>() to emptyList()
        }

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

    private enum class RiverValidationResult {
        PASSED, PRECONDITION_FAILED, VALIDATION_FAILED
    }
    private fun configureRiver(river: River): River =
        river.register(object : River.PacketListener {
            override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
                gotPacket = packet
                gotMessage = true
                validationResult = RiverValidationResult.PASSED
            }

            override fun onPreconditionError(
                error: MessageProblems,
                context: MessageContext,
                metadata: MessageMetadata
            ) {
                messageProblems = error
                validationResult = RiverValidationResult.PRECONDITION_FAILED
            }

            override fun onSevere(
                error: MessageProblems.MessageException,
                context: MessageContext
            ) {
                messageProblems = error.problems
                validationResult = RiverValidationResult.PRECONDITION_FAILED
            }

            override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
                messageProblems = problems
                validationResult = RiverValidationResult.VALIDATION_FAILED
            }
        })
}
