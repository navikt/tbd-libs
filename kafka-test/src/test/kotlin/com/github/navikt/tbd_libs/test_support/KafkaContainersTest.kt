package com.github.navikt.tbd_libs.test_support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.Serializer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration

class KafkaContainersTest {
    private companion object {
        private const val MAX_TOPICS_SIZE = 4
        private val kafkaContainer = KafkaContainers.container("tbd-libs-kafka-test", numberOfTopics = MAX_TOPICS_SIZE)
    }

    @Test
    fun `tømmer bassenget`() {
        val topics = (1.. MAX_TOPICS_SIZE).map { kafkaContainer.nyTopic() }
        assertThrows<RuntimeException> { kafkaContainer.nyTopic(Duration.ofMillis(10)) }
        topics.forEach { kafkaContainer.droppTopic(it) }
    }

    @Test
    fun `test 1`() = kafkaTest(kafkaContainer) {
        @Language("JSON")
        val message = """{ "name":  "Foo" }"""
        send(message)
        pollRecords().also {
            assertEquals(1, it.size)
            assertEquals(message, it.single().value())
        }
    }

    @Test
    fun `test 2`() = kafkaTest(kafkaContainer) {
        @Language("JSON")
        val message = """{ "name":  "Bar" }"""
        send(message)
        pollRecords().also {
            assertEquals(1, it.size)
            assertEquals(message, it.single().value())
        }
    }

    @Test
    fun `cleans up after use`() {
        lateinit var testTopic: TestTopic
        kafkaTest(kafkaContainer) {
            testTopic = this
            @Language("JSON")
            val message = """{ "name":  "Bar" }"""
            repeat(1000) { send(message) }
            pollRecords().also {
                assertEquals(500, it.size)
            }
        }
        testTopic.pollRecords().also {
            assertEquals(0, it.size) { "det skal ikke ligge rester igjen på topic etter test" }
        }
    }

    @Test
    fun `custom serde`() {
        val objectMapper = jacksonObjectMapper()
        kafkaTest(kafkaContainer) {
            @Language("JSON")
            val message = objectMapper.readTree("""{ "name":  "Bar" }""")
            val jacksonSerde = JacksonSerde(objectMapper)
            val stringSerde = StringSerde()
            send(message, jacksonSerde.serializer())
            pollRecords(stringSerde.deserializer(), jacksonSerde.deserializer()).also {
                assertEquals(1, it.size)
                assertEquals(message, it.single().value())
            }
        }
    }

    private class JacksonSerde(private val objectMapper: ObjectMapper = jacksonObjectMapper()) : Serde<JsonNode> {
        override fun serializer() = object : Serializer<JsonNode> {
            override fun serialize(topic: String, data: JsonNode) =
                objectMapper.writeValueAsBytes(data)
        }

        override fun deserializer() = object : Deserializer<JsonNode> {
            override fun deserialize(topic: String, data: ByteArray) =
                objectMapper.readTree(data)
        }
    }
}