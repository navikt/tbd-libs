package com.github.navikt.tbd_libs.rapids_and_rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.River.PacketListener.Companion.Name
import com.github.navikt.tbd_libs.rapids_and_rivers_api.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan

class River(rapidsConnection: RapidsConnection, private val randomIdGenerator: RandomIdGenerator = RandomIdGenerator.Default) : RapidsConnection.MessageListener {
    private val preconditions = mutableListOf<PacketValidation>()
    private val validations = mutableListOf<PacketValidation>()

    private val listeners = mutableListOf<PacketListener>()

    init {
        rapidsConnection.register(this)
    }

    fun precondition(validation: PacketValidation): River {
        preconditions.add(validation)
        return this
    }

    fun validate(validation: PacketValidation): River {
        validations.add(validation)
        return this
    }

    fun onSuccess(listener: PacketValidationSuccessListener): River {
        listeners.add(DelegatedPacketListener(listener))
        return this
    }

    fun onError(listener: PacketValidationErrorListener): River {
        listeners.add(DelegatedPacketListener(listener))
        return this
    }

    fun register(listener: PacketListener): River {
        listeners.add(listener)
        return this
    }

    override fun onMessage(message: String, context: MessageContext, metadata: MessageMetadata, metrics: MeterRegistry) {
        val problems = MessageProblems(message)
        try {
            val packet = JsonMessage(message, problems, randomIdGenerator)
            preconditions.forEach { it.validate(packet) }
            if (problems.hasErrors()) return onPreconditionError(metrics, problems, context, metadata)
            validations.forEach { it.validate(packet) }
            if (problems.hasErrors()) return onError(metrics, problems, context, metadata)
            onPacket(packet, JsonMessageContext(context, packet), metadata, metrics)
        } catch (err: MessageProblems.MessageException) {
            return onSevere(metrics, err, context)
        }
    }

    private fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, metrics: MeterRegistry) {
        val recognizedKeys = packet.keys
        val eventName = if ("@event_name" in recognizedKeys) packet["@event_name"].asText() else "ukjent"
        listeners.forEach {
            notifyPacketListener(metrics, eventName, it, packet, recognizedKeys, context, metadata)
        }
    }

    @WithSpan
    private fun notifyPacketListener(metrics: MeterRegistry, @SpanAttribute("eventName") eventName: String, packetListener: PacketListener, packet: JsonMessage, recognizedKeys: Set<String>, context: MessageContext, metadata: MessageMetadata) {
        onMessageCounter(metrics, context.rapidName(), packetListener.name(), "ok", eventName)
        logRecognizedKeys(metrics, context.rapidName(), packetListener.name(), eventName, recognizedKeys)
        val timer = Timer.start(metrics)
        packetListener.onPacket(packet, context, metadata, metrics)
        timer.stop(
            Timer.builder("on_packet_seconds")
            .description("Hvor lang det tar å lese en gjenkjent melding i sekunder")
            .tag("rapid", context.rapidName())
            .tag("river", packetListener.name())
            .tag("event_name", eventName)
            .register(metrics)
        )
    }

    private fun logRecognizedKeys(metrics: MeterRegistry, rapidName: String, riverName: String, eventName: String, keys: Set<String>) {
        keys.forEach { key ->
            Counter.builder("message_keys_counter")
                .description("Hvilke nøkler som er i bruk")
                .tag("rapid", rapidName)
                .tag("river", riverName)
                .tag("event_name", eventName)
                .tag("accessor_key", key)
                .register(metrics)
                .increment()
        }
    }

    private fun onSevere(metrics: MeterRegistry, error: MessageProblems.MessageException, context: MessageContext) {
        listeners.forEach {
            onMessageCounter(metrics, context.rapidName(), it.name(), "severe")
            it.onSevere(error, context)
        }
    }

    private fun onPreconditionError(metrics: MeterRegistry, problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        Span.current().setAttribute("nav.rapid_and_rivers.message.onPreconditionError", true)
        listeners.forEach {
            onMessageCounter(metrics, context.rapidName(), it.name(), "severe")
            it.onPreconditionError(problems, context, metadata)
        }
    }

    private fun onError(metrics: MeterRegistry, problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        listeners.forEach {
            onMessageCounter(metrics, context.rapidName(), it.name(), "error")
            it.onError(problems, context, metadata)
        }
    }

    private fun onMessageCounter(metrics: MeterRegistry, rapidName: String, riverName: String, validated: String, eventName: String? = null) {
        Counter.builder("message_counter")
            .description("Hvor mange meldinger som er lest inn")
            .tag("rapid", rapidName)
            .tag("river", riverName)
            .tag("validated", validated)
            .tag("event_name", eventName ?: "")
            .register(metrics)
            .increment()
    }


    fun interface PacketValidation {
        fun validate(message: JsonMessage)
    }

    fun interface PacketValidationSuccessListener {
        fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry)
    }

    fun interface PacketValidationErrorListener {
        fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata)
    }

    interface PacketListener : PacketValidationErrorListener, PacketValidationSuccessListener {
        companion object {
            fun Name(obj: Any) = obj::class.simpleName ?: "ukjent"
        }
        override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {}

        fun onPreconditionError(error: MessageProblems, context: MessageContext, metadata: MessageMetadata) {}
        fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {}

        fun name(): String = Name(this)
    }

    private class DelegatedPacketListener private constructor(
        private val packetHandler: PacketValidationSuccessListener,
        private val errorHandler: PacketValidationErrorListener
    ) : PacketListener {
        constructor(packetHandler: PacketValidationSuccessListener) : this(packetHandler, { _, _, _ -> })
        constructor(errorHandler: PacketValidationErrorListener) : this({ _, _, _, _ -> }, errorHandler)

        override fun name() = Name(packetHandler)

        override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
            errorHandler.onError(problems, context, metadata)
        }

        override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
            packetHandler.onPacket(packet, context, metadata, meterRegistry)
        }
    }

}
