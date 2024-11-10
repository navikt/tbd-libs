package com.github.navikt.tbd_libs.test_support

import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
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

    private val beginningOffsets = mutableMapOf<TopicPartition, Long>()
    private val activePartitions = mutableListOf<TopicPartition>()
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
            subscribe(listOf(topicnavn), object : ConsumerRebalanceListener {
                override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                    println("> $topicnavn mister partisjoner: ${partitions.joinToString()}")
                    activePartitions.removeAll(partitions)
                }

                override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                    println("> $topicnavn får partisjoner: ${partitions.joinToString()}")
                    activePartitions.addAll(partitions)
                    if (beginningOffsets.isEmpty()) return
                    partitions
                        .filter { it in beginningOffsets }
                        .forEach {
                            val nextOffsetForPartition = beginningOffsets.getValue(it)
                            println("> setter posisjon for ${it.topic()} @ #${it.partition()} til $nextOffsetForPartition")
                            seek(it, nextOffsetForPartition)
                        }
                    beginningOffsets.clear()
                }
            })
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
        println("> Rydder opp og forbereder gjenbruk i $topicnavn - ${Thread.currentThread()}")
        measureTime {
            // "leser" meldingene som evt. ikke er lest slik
            // at consumeren er klar til neste test
            lesForbiSendteMeldinger()
            producedMessages.clear()
        }.also {
            println("> Brukte ${it.inWholeMilliseconds} ms på opprydding")
        }
    }

    private fun lesForbiSendteMeldinger() {
        // sørger for at usendte ting flushes og sendes
        producer.flush()
        producedMessages.forEach { it.get() }

        val offsetsForNextPoll = producedMessages
            .map { it.get() }
            .groupBy ({ TopicPartition(topicnavn, it.partition()) }) { it.offset() }
            .mapValues { (_, offsets) -> offsets.max() + 1 }

        if (consumer.assignment().isEmpty()) {
            beginningOffsets.putAll(offsetsForNextPoll)
        } else {
            offsetsForNextPoll.forEach { (partition, offset) -> consumer.seek(partition, offset) }
        }
    }

    fun send(message: String): Future<RecordMetadata> = send(message, strings.serializer())
    fun send(key: String, message: String?) = send(key, message, strings.serializer(), strings.serializer())

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

    fun pollRecords(timeout: Duration = Duration.ofMillis(100), maxWaitForAtLeastOneRecord: Duration = Duration.ofSeconds(5)) =
        pollRecords(strings.deserializer(), strings.deserializer(), timeout, maxWaitForAtLeastOneRecord)

    fun <K, V> pollRecords(keyDeserializer: Deserializer<K>, valueDeserializer: Deserializer<V>, timeout: Duration = Duration.ofMillis(100), maxWaitForAtLeastOneRecord: Duration = Duration.ofSeconds(5)): List<ConsumerRecord<K, V>> {
        producer.flush()
        producedMessages.forEach { it.get() }
        println("> Consumerer meldinger fra $topicnavn (consumer position ${activePartitions.joinToString { "${it.topic()} @ #${it.partition()} - offset ${consumer.position(it)}" }}) - ${Thread.currentThread()}")
        return buildList {
            try {
                pollUntilAtLeastOneRecordOrTimeout(keyDeserializer, valueDeserializer, timeout, maxWaitForAtLeastOneRecord)
            } catch (_: WakeupException) {
                println("> Consumer av $topicnavn fikk beskjed om å våkne opp - ${Thread.currentThread()}")
            }
        }.also {
            println("> Consumer av $topicnavn fikk ${it.size} records - ${Thread.currentThread()}")
        }
    }

    private fun <K, V> MutableList<ConsumerRecord<K, V>>.pollUntilAtLeastOneRecordOrTimeout(keyDeserializer: Deserializer<K>, valueDeserializer: Deserializer<V>, timeout: Duration, maxWaitForAtLeastOneRecord: Duration) {
        val start = System.currentTimeMillis()
        while (isEmpty() && (System.currentTimeMillis() - start) < maxWaitForAtLeastOneRecord.toMillis()) {
            val pollStart = System.currentTimeMillis()
            val records = consumer.poll(timeout)
            val pollEnd = System.currentTimeMillis()
            println("> Poll av $topicnavn returnerte ${records.count()} records etter ${pollEnd - pollStart} ms (consumer position ${activePartitions.joinToString { "${it.topic()} @ #${it.partition()} - offset ${consumer.position(it)}" }})")
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
