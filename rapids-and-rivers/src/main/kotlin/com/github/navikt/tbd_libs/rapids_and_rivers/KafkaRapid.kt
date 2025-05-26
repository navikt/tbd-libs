package com.github.navikt.tbd_libs.rapids_and_rivers

import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers_api.KeyMessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

class KafkaRapid(
    factory: ConsumerProducerFactory,
    groupId: String,
    private val rapidTopic: String,
    private val meterRegistry: MeterRegistry,
    consumerProperties: Properties = Properties(),
    producerProperties: Properties = Properties(),
    private val autoCommit: Boolean = false,
    extraTopics: List<String> = emptyList(),
) : RapidsConnection(), ConsumerRebalanceListener {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val running = AtomicBoolean(Stopped)
    private val ready = AtomicBoolean(false)
    private val producerClosed = AtomicBoolean(false)

    private val consumer = factory.createConsumer(groupId, consumerProperties.apply {
        if (!autoCommit) put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }, withShutdownHook = false)
    private val producer = factory.createProducer(producerProperties, withShutdownHook = false)

    private val topics = listOf(rapidTopic) + extraTopics

    init {
        log.info("rapid initialized, autoCommit=$autoCommit")
    }

    fun isRunning() = running.get()
    fun isReady() = isRunning() && ready.get()

    override fun publish(message: String) {
        publishBulk(listOf(OutgoingMessage(message)))
    }

    override fun publish(key: String, message: String) {
        publishBulk(listOf(OutgoingMessage(message, key)))
    }

    override fun publishBulk(messages: List<OutgoingMessage>) {
        publishRecordsInBulk(messages.map {
            when (it.key) {
                null -> ProducerRecord(rapidTopic, it.body)
                else -> ProducerRecord(rapidTopic, it.key, it.body)
            }
        })
    }

    override fun rapidName(): String {
        return rapidTopic
    }

    private fun publishRecordsInBulk(producerRecords: List<ProducerRecord<String, String>>) {
        check(!producerClosed.get()) { "can't publish messages when producer is closed" }

        val result = producerRecords
            .map { it to producer.send(it) }
            .map { (record, future) ->
                try {
                    Triple(record, future.get(), null)
                } catch (err: Exception) {
                    Triple(record, null, err)
                }
            }

        // log outcome
        val metadataString = result
            .mapIndexed { index, (_, metadata, err) ->
                if (err == null)
                    "#$index: partition=${metadata!!.partition()}, offset=${metadata.offset()}"
                else
                    "#$index: FAILED: ${err.message}"
            }
            .joinToString(separator = "\n")
        log.info("produced ${result.size} message(s):\n$metadataString")

        val hasFailedMessages = result.any { (_, _, err) -> err != null }

        if (!hasFailedMessages) return

        /* handle all exceptions like fatal ones */
        log.error("Fatal error when sending messages: invoking shutdown!")
        // loops the list unfiltered to preserve the order of messages (i.e., the index of each item)
        result.forEachIndexed { index, (record, _, err) ->
            // should ideally log the record as well, so it can be inspected later?
            // for now, assume the caller has control over the messages and can log them if needed
            if (err != null) {
                log.error("Message #$index failed: ${err.message}", err)
            }
        }
        stop()
    }

    private fun registerMetrics() {
        KafkaClientMetrics(consumer).bindTo(meterRegistry)
        KafkaClientMetrics(producer).bindTo(meterRegistry)
    }

    override fun start() {
        log.info("starting rapid")
        if (Started == running.getAndSet(Started)) return log.info("rapid already started")
        registerMetrics()
        consumeMessages()
    }

    override fun stop() {
        log.info("stopping rapid")
        if (Stopped == running.getAndSet(Stopped)) return log.info("rapid already stopped")
        notifyShutdownSignal()
        tryAndLog { producer.flush() }
        consumer.wakeup()
    }

    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        if (partitions.isEmpty()) return
        log.info("partitions assigned: $partitions")
        notifyReady()
    }

    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        log.info("partitions revoked: $partitions")
        notifyNotReady()
    }

    private fun onRecords(records: ConsumerRecords<String, String>) {
        if (records.isEmpty) return // poll returns an empty collection in case of rebalancing
        val currentPositions = records
            .groupBy { TopicPartition(it.topic(), it.partition()) }
            .mapValues { it.value.minOf { it.offset() } }
            .toMutableMap()
        try {
            records
                .onEach { record ->
                    if (running.get()) {
                        onRecord(record)
                        currentPositions[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
                    }
                }
        } catch (err: Exception) {
            log.info(
                "due to an error during processing, positions are reset to each next message (after each record that was processed OK):" +
                        currentPositions.map { "\tpartition=${it.key}, offset=${it.value}" }
                            .joinToString(separator = "\n", prefix = "\n", postfix = "\n"), err
            )
            throw err
        } finally {
            val offsetsToBeCommitted = currentPositions.mapValues<TopicPartition, Long, OffsetAndMetadata> { (_, offset) -> offsetMetadata(offset) }
            log.info("committing offsets ${offsetsToBeCommitted.entries.joinToString { "topic=${it.key.topic()}, parition=${it.key.partition()} offset=${it.value.offset()}" }}")
            consumer.commitSync(offsetsToBeCommitted)
        }
    }

    private fun onRecord(record: ConsumerRecord<String, String>) {
        withMDC(recordDiganostics(record)) {
            val recordValue = record.value()
                ?: return@withMDC log.info("ignoring record with offset ${record.offset()} in partition ${record.partition()} because value is null (tombstone)")
            val context = KeyMessageContext(this, record.key())
            val metadata = MessageMetadata(
                topic = record.topic(),
                partition = record.partition(),
                offset = record.offset(),
                key = record.key(),
                headers = record.headers().associate { it.key() to it.value() }
            )
            notifyMessage(recordValue, context, metadata, meterRegistry)
        }
    }

    private fun consumeMessages() {
        var lastException: Exception? = null
        try {
            notifyStartup()
            ready.set(true)
            consumer.subscribe(topics, this)
            while (running.get()) {
                consumer.poll(Duration.ofSeconds(1)).also {
                    withMDC(pollDiganostics(it)) {
                        onRecords(it)
                    }
                }
            }
        } catch (err: WakeupException) {
            // throw exception if we have not been told to stop
            if (running.get()) throw err
        } catch (err: Exception) {
            lastException = err
            throw err
        } finally {
            notifyShutdown()
            closeResources(lastException)
            notifyShutdownComplete()
        }
    }

    private fun pollDiganostics(records: ConsumerRecords<String, String>) = mapOf(
        "rapids_poll_id" to "${UUID.randomUUID()}",
        "rapids_poll_time" to "${LocalDateTime.now()}",
        "rapids_poll_count" to "${records.count()}"
    )

    private fun recordDiganostics(record: ConsumerRecord<String, String>) = mapOf(
        "rapids_record_id" to "${UUID.randomUUID()}",
        "rapids_record_before_notify_time" to "${LocalDateTime.now()}",
        "rapids_record_produced_time" to "${record.timestamp()}",
        "rapids_record_produced_time_type" to "${record.timestampType()}",
        "rapids_record_topic" to record.topic(),
        "rapids_record_partition" to "${record.partition()}",
        "rapids_record_offset" to "${record.offset()}"
    )

    private fun offsetMetadata(offset: Long): OffsetAndMetadata {
        val clientId = consumer.groupMetadata().groupInstanceId().map { "\"$it\"" }.orElse("null")

        @Language("JSON")
        val metadata = """{"time": "${LocalDateTime.now()}","groupInstanceId": $clientId}"""
        return OffsetAndMetadata(offset, metadata)
    }

    private fun closeResources(lastException: Exception?) {
        if (Started == running.getAndSet(Stopped)) {
            log.warn("stopped consuming messages due to an error", lastException)
        } else {
            log.info("stopped consuming messages after receiving stop signal")
        }
        log.info("closing consumer")
        tryAndLog(consumer::close)
        producerClosed.set(true)
        log.info("flushing producer")
        tryAndLog(producer::flush)
        log.info("closing producer")
        tryAndLog(producer::close)
    }

    private fun tryAndLog(block: () -> Unit) {
        try {
            block()
        } catch (err: Exception) {
            log.error(err.message, err)
        }
    }

    companion object {
        private const val Stopped = false
        private const val Started = true
    }
}
