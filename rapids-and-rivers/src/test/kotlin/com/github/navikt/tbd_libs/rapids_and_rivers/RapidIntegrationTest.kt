package com.github.navikt.tbd_libs.rapids_and_rivers

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.test_support.KafkaContainers
import com.github.navikt.tbd_libs.test_support.TestTopic
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.random.Random

internal class RapidIntegrationTest {
    private companion object {
        private val kafkaContainer = KafkaContainers.container("tbd-rapid-and-rivers", minPoolSize = 2)
    }
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private fun rapidE2E(testblokk: suspend TestContext.(Job) -> Unit) = runBlocking {
        val (testTopic, extraTestTopic) = kafkaContainer.nyeTopics(2)
        try {
            val caller = Exception().stackTrace.last { it.className.contains(RapidIntegrationTest::class.java.packageName) }.methodName
            println("<$caller> får ${testTopic.topicnavn} og ${extraTestTopic.topicnavn}")
            startRapid(testTopic, extraTestTopic, testblokk)
        } finally {
            kafkaContainer.droppTopics(listOf(testTopic, extraTestTopic))
        }
    }

    private suspend fun CoroutineScope.startRapid(testTopic: TestTopic, extraTestTopic: TestTopic, testblokk: suspend TestContext.(Job) -> Unit) {
        val consumerGroupId = "kafkarapid-${Random.nextInt()}"
        val rapid = createTestRapid(consumerGroupId, testTopic, extraTestTopic)
        val job = launch(Dispatchers.IO) { rapid.start() }

        try {
            await("wait until the rapid has started")
                .atMost(20, SECONDS)
                .until {
                    rapid.isRunning()
                }

            testblokk(TestContext(consumerGroupId, rapid, testTopic, extraTestTopic), job)
        } finally {
            rapid.stop()
            job.cancelAndJoin()
        }
    }

    private data class TestContext(
        val consumerGroupId: String,
        val rapid: KafkaRapid,
        val mainTopic: TestTopic,
        val extraTopic: TestTopic
    )

    @Test
    fun `no effect calling start multiple times`() = rapidE2E {
        assertDoesNotThrow { rapid.start() }
        assertTrue(rapid.isRunning())
    }

    @Test
    fun `can stop`() = rapidE2E {
        rapid.stop()
        assertFalse(rapid.isRunning())
        assertDoesNotThrow { rapid.stop() }
    }

    @Test
    fun `should stop on errors`() {
        assertThrows<RuntimeException> {
            rapidE2E {
                rapid.register { _, _, _ -> throw RuntimeException("oh shit") }

                await("wait until the rapid stops")
                    .atMost(20, SECONDS)
                    .until {
                        mainTopic.send(UUID.randomUUID().toString())
                        !rapid.isRunning()
                    }
            }
        }.also {
            assertEquals("oh shit", it.message)
        }
    }

    @Test
    fun `in case of exception, the offset committed is the erroneous record`() = rapidE2E {
        ensureRapidIsActive()

        // stop rapid so we can queue up records
        rapid.stop()
        it.cancelAndJoin()

        val offsets = (0..100).map {
            val key = UUID.randomUUID().toString()
            mainTopic.send(key, "{\"test_message_index\": $it}").get()
        }
            .also {
                assertEquals(1, it.distinctBy { it.partition() }.size) { "testen forutsetter én unik partisjon" }
            }
            .map { it.offset() }

        val failOnMessage = 50
        val expectedOffset = offsets[failOnMessage]
        var readFailedMessage = false

        val rapid = createTestRapid(consumerGroupId, mainTopic, extraTopic)

        River(rapid)
            .validate { it.requireKey("test_message_index") }
            .onSuccess { packet: JsonMessage, _: MessageContext ->
                val index = packet["test_message_index"].asInt()
                println("Read test_message_index=$index")
                if (index == failOnMessage) {
                    readFailedMessage = true
                    throw RuntimeException("an unexpected error happened")
                }
            }

        try {
            runBlocking(Dispatchers.IO) { launch { rapid.start() } }
        } catch (err: RuntimeException) {
            assertEquals("an unexpected error happened", err.message)
        } finally {
            rapid.stop()
        }

        await("wait until the rapid stops")
                .atMost(20, SECONDS)
                .until { !rapid.isRunning() }

        val actualOffset = await().atMost(Duration.ofSeconds(5)).until({
            val offsets = mainTopic.adminClient
                .listConsumerGroupOffsets(consumerGroupId)
                ?.partitionsToOffsetAndMetadata()
                ?.get()
                ?: fail { "was not able to fetch committed offset for consumer $consumerGroupId" }
            offsets[TopicPartition(mainTopic.topicnavn, 0)]
        }) { it != null }

        val metadata = actualOffset?.metadata() ?: fail { "expected metadata to be present in OffsetAndMetadata" }
        assertEquals(expectedOffset, actualOffset.offset())
        assertTrue(objectMapper.readTree(metadata).has("groupInstanceId"))
        assertDoesNotThrow { LocalDateTime.parse(objectMapper.readTree(metadata).path("time").asText()) }
    }

    private fun TestContext.ensureRapidIsActive() {
        val readMessages = mutableListOf<JsonMessage>()
        River(rapid).onSuccess { packet: JsonMessage, _: MessageContext -> readMessages.add(packet) }

        await("wait until the rapid has read the test message")
                .atMost(5, SECONDS)
                .until {
                    rapid.publish("{\"foo\": \"bar\"}")
                    readMessages.isNotEmpty()
                }
    }

    @Test
    fun `ignore tombstone messages`() = rapidE2E {
        val serviceId = "my-service"
        val eventName = "heartbeat"

        testRiver(eventName, serviceId)
        println("sender null-melding på ${mainTopic.topicnavn}")
        val recordMetadata = mainTopic.waitForReply(serviceId, eventName, null)

        val actualOffset = await().atMost(Duration.ofSeconds(5)).until({
            val offsets = mainTopic.adminClient
                .listConsumerGroupOffsets(consumerGroupId)
                ?.partitionsToOffsetAndMetadata()
                ?.get()
                ?: fail { "was not able to fetch committed offset for consumer $consumerGroupId" }
            offsets[TopicPartition(recordMetadata.topic(), recordMetadata.partition())]
        }) { it != null }
        val metadata = actualOffset?.metadata() ?: fail { "expected metadata to be present in OffsetAndMetadata" }
        assertTrue(actualOffset.offset() >= recordMetadata.offset()) { "expected $actualOffset to be equal or greater than $recordMetadata" }
        assertTrue(objectMapper.readTree(metadata).has("groupInstanceId"))
        assertDoesNotThrow { LocalDateTime.parse(objectMapper.readTree(metadata).path("time").asText()) }
    }

    @Test
    fun `read and produce message`() = rapidE2E {
        val serviceId = "my-service"
        val eventName = "heartbeat"
        val value = "{ \"@event\": \"$eventName\" }"

        testRiver(eventName, serviceId)
        val recordMetadata = mainTopic.waitForReply(serviceId, eventName, value)

        val actualOffset = await().atMost(Duration.ofSeconds(5)).until({
            val offsets = mainTopic.adminClient
                .listConsumerGroupOffsets(consumerGroupId)
                ?.partitionsToOffsetAndMetadata()
                ?.get()
                ?: fail { "was not able to fetch committed offset for consumer $consumerGroupId" }
            offsets[TopicPartition(recordMetadata.topic(), recordMetadata.partition())]
        }) { it != null }
        val metadata = actualOffset?.metadata() ?: fail { "expected metadata to be present in OffsetAndMetadata" }
        assertTrue(actualOffset.offset() >= recordMetadata.offset())
        assertTrue(objectMapper.readTree(metadata).has("groupInstanceId"))
        assertDoesNotThrow { LocalDateTime.parse(objectMapper.readTree(metadata).path("time").asText()) }
    }

    @Test
    fun `read from others topics and produce to rapid topic`() = rapidE2E {
        val serviceId = "my-service"
        val eventName = "heartbeat"
        val value = "{ \"@event\": \"$eventName\" }"

        testRiver(eventName, serviceId)
        extraTopic.waitForReply(serviceId, eventName, value)
    }

    private fun createTestRapid(consumerGroupId: String, mainTopic: TestTopic, extraTopic: TestTopic): KafkaRapid {
        val localConfig = LocalKafkaConfig(kafkaContainer.connectionProperties)
        val factory: ConsumerProducerFactory = ConsumerProducerFactory(localConfig)
        return KafkaRapid(factory, consumerGroupId, mainTopic.topicnavn, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), extraTopics = listOf(extraTopic.topicnavn))
    }

    private fun TestContext.testRiver(eventName: String, serviceId: String) {
        River(rapid).apply {
            validate { it.requireValue("@event", eventName) }
            validate { it.forbid("service_id") }
            register(object : River.PacketListener {
                override fun onPacket(packet: JsonMessage, context: MessageContext) {
                    packet["service_id"] = serviceId
                    context.publish(packet.toJson())
                }

                override fun onError(problems: MessageProblems, context: MessageContext) {}
            })
        }
    }

    private fun TestTopic.waitForReply(serviceId: String, eventName: String, event: String?): RecordMetadata {
        val sentMessages = mutableListOf<String>()
        val key = UUID.randomUUID().toString()
        val recordMetadata = send(key, event).get(5000, SECONDS)
        sentMessages.add(key)
        await("wait until we get a reply")
            .atMost(20, SECONDS)
            .until {
                runBlocking {
                    pollRecords().any {
                        sentMessages.contains(it.key()) && it.key() == key
                    }
                }
            }
        return recordMetadata
    }

}

private class LocalKafkaConfig(private val connectionProperties: Properties) : Config {
    override fun producerConfig(properties: Properties): Properties {
        return properties.apply {
            putAll(connectionProperties)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
            put(ProducerConfig.RETRIES_CONFIG, "0")
        }
    }

    override fun consumerConfig(groupId: String, properties: Properties): Properties {
        return properties.apply {
            putAll(connectionProperties)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
    }

    override fun adminConfig(properties: Properties): Properties {
        return properties.apply {
            putAll(connectionProperties)
        }
    }
}