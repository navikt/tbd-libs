package com.github.navikt.tbd_libs.test_support

import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.CommonClientConfigs
import org.testcontainers.DockerClientFactory
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class KafkaContainer(
    private val appnavn: String,
    private val poolSize: Int
) {
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

    private val tilgjengeligeTopics by lazy { ArrayBlockingQueue<TestTopic>(poolSize, false, opprettTopics()) }
    private val connectionProperties by lazy {
        Properties().apply {
            println("Bootstrap servers: ${instance.bootstrapServers}")
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, instance.bootstrapServers)
        }
    }

    fun nyTopic(timeout: Duration = Duration.ofSeconds(20)): TestTopic {
        return tilgjengeligeTopics.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) ?: topicIkkeTilgjengelig(timeout)
    }

    private fun topicIkkeTilgjengelig(timeout: Duration): Nothing {
        throw RuntimeException("Ventet i ${timeout.toMillis()} millisekunder uten å få en ledig topic")
    }

    private fun opprettTopics() =
        (1..poolSize).map { TestTopic("test.topic.$it", connectionProperties) }

    fun droppTopic(testTopic: TestTopic) {
        println("Tilgjengeliggjør ${testTopic.topicnavn} igjen")
        check(tilgjengeligeTopics.offer(testTopic)) {
            "Kunne ikke returnere ${testTopic.topicnavn}"
        }
    }
}

fun kafkaTest(kafkaContainer: KafkaContainer, testBlock: suspend TestTopic.() -> Unit) {
    runBlocking {
        val testTopic = kafkaContainer.nyTopic()
        try {
            testBlock(testTopic)
        } finally {
            testTopic.cleanUp()
            kafkaContainer.droppTopic(testTopic)
        }
    }
}