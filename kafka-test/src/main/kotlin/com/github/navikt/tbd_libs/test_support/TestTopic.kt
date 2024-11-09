package com.github.navikt.tbd_libs.test_support

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes.ByteArraySerde
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.Serializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Future
import kotlin.time.measureTime

class TestTopic(
    val topicnavn: String,
    private val connectionProperties: Properties
) {
    private val bytes = ByteArraySerde()
    private val strings = StringSerde()

    private val activePartitions = mutableListOf<TopicPartition>()
    private val rebalanceListener = object : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            activePartitions.removeAll(partitions)
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            activePartitions.addAll(partitions)
        }
    }
    val producer by lazy {
        val producerProperties = Properties().apply {
            putAll(connectionProperties)
            put(ProducerConfig.LINGER_MS_CONFIG, "0")
        }
        KafkaProducer(producerProperties, bytes.serializer(), bytes.serializer())
    }

    val consumer by lazy {
        val consumerProperties = Properties().apply {
            putAll(connectionProperties)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-$topicnavn")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }
        KafkaConsumer(consumerProperties, bytes.deserializer(), bytes.deserializer()).apply {
            subscribe(listOf(topicnavn), rebalanceListener)
        }
    }

    val adminClient by lazy {
        AdminClient.create(connectionProperties)
    }

    private val producedMessages = mutableListOf<Future<RecordMetadata>>()

    init {
        println("> Oppretter topic med topicnavn=$topicnavn")
    }

    fun cleanUp() {
        println("> Rydder opp og forbereder gjenbruk i $topicnavn")
        measureTime {
            // flusher producer hvis sendekøen ikke er tom
            producer.flush()
            // "leser" meldingene som evt. ikke er lest
            lesForbiSendteMeldinger()
            producedMessages.clear()
        }.also {
            println("> Brukte ${it.inWholeMilliseconds} ms på opprydding")
        }
    }

    private fun lesForbiSendteMeldinger() {
        // dette er nok en enklere variant enn å committe offsets
        return consumer.seekToEnd(activePartitions)

        val offsets = producedMessages
            .map { it.get() }
            .groupBy { TopicPartition(topicnavn, it.partition()) }
            .mapValues { (_, offsets) ->
                OffsetAndMetadata(offsets.maxOf { it.offset() } + 1)
            }
        consumer.commitSync(offsets)
    }

    fun send(message: String): Future<RecordMetadata> = send(message, strings.serializer())
    fun send(key: String, message: String) = send(key, message, strings.serializer(), strings.serializer())

    fun <V> send(message: V, valueSerializer: Serializer<V>) =
        send(ProducerRecord(topicnavn, valueSerializer.serialize(topicnavn, message)))

    fun <K, V> send(key: K, message: V, keySerializer: Serializer<K>, valueSerializer: Serializer<V>) =
        send(ProducerRecord(topicnavn, keySerializer.serialize(topicnavn, key), valueSerializer.serialize(topicnavn, message)))

    private fun send(record: ProducerRecord<ByteArray, ByteArray>): Future<RecordMetadata> {
        println("> Sender melding #${producedMessages.size + 1} for topic $topicnavn")
        return producer.send(record).also {
            producedMessages.add(it)
        }
    }

    fun pollRecords(timeout: Duration = Duration.ofMillis(50), maxWaitForAtLeastOneRecord: Duration = Duration.ofSeconds(1)) =
        pollRecords(strings.deserializer(), strings.deserializer(), timeout, maxWaitForAtLeastOneRecord)

    fun <K, V> pollRecords(keyDeserializer: Deserializer<K>, valueDeserializer: Deserializer<V>, timeout: Duration = Duration.ofMillis(50), maxWaitForAtLeastOneRecord: Duration = Duration.ofSeconds(1)): List<ConsumerRecord<K, V>> {
        producer.flush()
        println("> Consumerer meldinger fra $topicnavn")
        //printProducedMessages()
        return buildList {
            val start = System.currentTimeMillis()
            while (isEmpty() && (System.currentTimeMillis() - start) < maxWaitForAtLeastOneRecord.toMillis()) {
                val pollStart = System.currentTimeMillis()
                val records = consumer.poll(timeout)
                val pollEnd = System.currentTimeMillis()
                println("> Poll returnerte ${records.count()} records etter ${pollEnd - pollStart} ms")
                records.forEach {
                    val key = keyDeserializer.deserialize(it.topic(), it.headers(), it.key())
                    val value = valueDeserializer.deserialize(it.topic(), it.headers(), it.value())

                    val copy = ConsumerRecord<K, V>(
                        /* topic = */ it.topic(),
                        /* partition = */ it.partition(),
                        /* offset = */ it.offset(),
                        /* timestamp = */ it.timestamp(),
                        /* timestampType = */ it.timestampType(),
                        /* serializedKeySize = */ it.key()?.size ?: NULL_SIZE,
                        /* serializedValueSize = */ it.value()?.size ?: NULL_SIZE,
                        /* key = */ key,
                        /* value = */ value,
                        /* headers = */ it.headers(),
                        /* leaderEpoch = */ it.leaderEpoch()
                    )
                    add(copy)
                }
            }
        }
    }

    private fun printProducedMessages() {
        val finished = producedMessages
            .filter { it.state() == Future.State.SUCCESS }

        finished.map { it.resultNow() }
            .onEach {
                println("${it.topic()} - partition ${it.partition()} - offset ${it.offset()}")
            }
        producedMessages.removeAll(finished)
        producedMessages
            .forEachIndexed { index, it ->
                println("> Melding #${index + 1} -> ${it.state()}")
            }
    }
}