package com.github.navikt.tbd_libs.test_support

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.kafka.clients.CommonClientConfigs
import org.testcontainers.DockerClientFactory
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

class KafkaContainer(
    private val appnavn: String,
    private val poolSize: Int
) {
    private companion object {
        private const val IKKE_INITIALISERT = false
        private const val ER_INITIALISERT = true
    }
    private val instance by lazy {
        ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1")).apply {
            withCreateContainerCmdModifier { command -> command.withName(appnavn) }
            withReuse(true)
            withLabel("app-navn", appnavn)
            DockerClientFactory.lazyClient().apply {
                this
                    .listContainersCmd()
                    .exec()
                    .filter { it.labels["app-navn"] == appnavn }
                    .forEach {
                        killContainerCmd(it.id).exec()
                        removeContainerCmd(it.id).withForce(true).exec()
                    }
            }
            println("Starting kafka container")
            start()
        }
    }

    private val topicsInitialized = AtomicBoolean(IKKE_INITIALISERT)
    private val tilgjengeligeTopics = Channel<TestTopic>(poolSize)

    val connectionProperties by lazy {
        Properties().apply {
            println("Bootstrap servers: ${instance.bootstrapServers}")
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, instance.bootstrapServers)
        }
    }

    suspend fun nyTopic(timeout: Duration = Duration.ofSeconds(20)): TestTopic {
        return nyeTopics(1, timeout).single()
    }

    suspend fun nyeTopics(antall: Int, timeout: Duration = Duration.ofSeconds(20)): List<TestTopic> {
        opprettTopicsHvisIkkeInitialisert()
        return fåTopicsOrThrow(antall, timeout)
    }
    private suspend fun fåTopicsOrThrow(antall: Int, timeout: Duration): List<TestTopic> {
        val claimedTopics = fåTopics(antall, timeout)
        if (claimedTopics.size != antall) {
            droppTopics(claimedTopics)
            throw RuntimeException("Fikk kun tak i ${claimedTopics.size} av $antall")
        }
        println("> Får topic ${claimedTopics.joinToString { it.topicnavn} }")
        return claimedTopics
    }

    private suspend fun fåTopics(antall: Int, timeout: Duration): List<TestTopic> {
        return buildList {
            (1..antall).forEach {
                withTimeoutOrNull(timeout.toKotlinDuration()) {
                    add(tilgjengeligeTopics.receive())
                } ?: return@buildList
            }
        }
    }

    private suspend fun opprettTopicsHvisIkkeInitialisert() {
        if (!topicsInitialized.compareAndSet(IKKE_INITIALISERT, ER_INITIALISERT)) return
        withTimeout(10.seconds) {
            repeat(poolSize) {
                tilgjengeligeTopics.send(TestTopic("test.topic.$it", connectionProperties))
            }
        }
    }

    suspend fun droppTopic(testTopic: TestTopic) {
        droppTopics(listOf(testTopic))
    }

    suspend fun droppTopics(testTopics: List<TestTopic>) {
        println("Tilgjengeliggjør ${testTopics.joinToString { it.topicnavn }} igjen")
        testTopics.forEach { it.cleanUp() }
        withTimeout(20.seconds) { testTopics.forEach { tilgjengeligeTopics.send(it) } }
    }
}

fun kafkaTest(kafkaContainer: KafkaContainer, testBlock: suspend TestTopic.() -> Unit) {
    runBlocking {
        val testTopic = kafkaContainer.nyTopic()
        try {
            testBlock(testTopic)
        } finally {
            kafkaContainer.droppTopic(testTopic)
        }
    }
}