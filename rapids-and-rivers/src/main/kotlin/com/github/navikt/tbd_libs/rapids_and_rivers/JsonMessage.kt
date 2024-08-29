package com.github.navikt.tbd_libs.rapids_and_rivers


import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RandomIdGenerator
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.InetAddress
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

// Understands a specific JSON-formatted message
open class JsonMessage(
    originalMessage: String,
    private val problems: MessageProblems,
    private val metrics: MeterRegistry,
    randomIdGenerator: RandomIdGenerator? = null
) {
    private val idGenerator = randomIdGenerator ?: RandomIdGenerator.Default
    val id: String

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        private const val nestedKeySeparator = '.'
        private const val IdKey = "@id"
        private const val OpprettetKey = "@opprettet"
        private const val EventNameKey = "@event_name"
        private const val NeedKey = "@behov"
        private const val ReadCountKey = "system_read_count"
        private const val ParticipatingServicesKey = "system_participating_services"

        private val serviceName: String? = System.getenv("NAIS_APP_NAME")
        private val serviceImage: String? = System.getenv("NAIS_APP_IMAGE")
        private val serviceHostname = serviceName?.let { InetAddress.getLocalHost().hostName }

        fun newMessage(
            map: Map<String, Any> = emptyMap(),
            metrics: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            randomIdGenerator: RandomIdGenerator? = null
        ) = objectMapper.writeValueAsString(map).let {
            JsonMessage(it, MessageProblems(it), metrics, randomIdGenerator)
        }

        fun newMessage(
            eventName: String,
            map: Map<String, Any> = emptyMap(),
            metrics: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            randomIdGenerator: RandomIdGenerator? = null
        ) = newMessage(mapOf(EventNameKey to eventName) + map, metrics, randomIdGenerator)

        fun newNeed(
            behov: Collection<String>,
            map: Map<String, Any> = emptyMap(),
            metrics: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            randomIdGenerator: RandomIdGenerator? = null
        ) = newMessage("behov", mapOf(
            "@behovId" to UUID.randomUUID(),
            NeedKey to behov
        ) + map, metrics, randomIdGenerator)

        internal fun populateStandardFields(originalMessage: JsonMessage, message: String, randomIdGenerator: RandomIdGenerator = originalMessage.idGenerator): String {
            return (objectMapper.readTree(message) as ObjectNode).also {
                it.replace("@forårsaket_av", objectMapper.valueToTree(originalMessage.tracing))
                if (it.path("@id").isMissingOrNull() || it.path("@id").asText() == originalMessage.id) {
                    val id = randomIdGenerator.generateId()
                    val opprettet = LocalDateTime.now()
                    it.put(IdKey, id)
                    it.put(OpprettetKey, "$opprettet")
                    initializeOrSetParticipatingServices(it, id, opprettet)
                }
            }.toString()
        }

        private fun initializeOrSetParticipatingServices(node: JsonNode, id: String, opprettet: LocalDateTime) {
            val entry = mutableMapOf(
                "id" to id,
                "time" to "$opprettet"
            ).apply {
                compute("service") { _, _ -> serviceName }
                compute("instance") { _, _ -> serviceHostname }
                compute("image") { _, _ -> serviceImage }
            }
            if (node.path(ParticipatingServicesKey).isMissingOrNull()) (node as ObjectNode).putArray(
                ParticipatingServicesKey
            ).add(objectMapper.valueToTree<ObjectNode>(entry))
            else (node.path(ParticipatingServicesKey) as ArrayNode).add(objectMapper.valueToTree<JsonNode>(entry))
        }

        private fun parseMessageAsJsonObject(message: String, problems: MessageProblems): ObjectNode {
            val jsonNode = try {
                objectMapper.readTree(message)
            } catch (err: JsonParseException) {
                problems.severe("Invalid JSON per Jackson library: ${err.message}")
            }
            if (!jsonNode.isObject) problems.severe("Incomplete json. Should be able to cast as ObjectNode.")
            return jsonNode as ObjectNode
        }
    }

    private val json: ObjectNode
    private val recognizedKeys = mutableMapOf<String, JsonNode>()

    init {
        json = parseMessageAsJsonObject(originalMessage, problems)
        id = json.path("@id").takeUnless { it.isMissingOrNull() }?.asText() ?: idGenerator.generateId().also {
            set("@id", it)
        }
        val opprettet = LocalDateTime.now()
        if (!json.hasNonNull("@opprettet")) set(OpprettetKey, opprettet)
        set(ReadCountKey, json.path(ReadCountKey).asInt(-1) + 1)
        initializeOrSetParticipatingServices(json, id, opprettet)
    }

    private val tracing =
        mutableMapOf<String, Any>(
            "id" to json.path(IdKey).asText()
        ).apply {
            compute("opprettet") { _, _ -> json.path(OpprettetKey).asText().takeUnless { it.isBlank() } }
            compute("event_name") { _, _ -> json.path(EventNameKey).asText().takeUnless { it.isBlank() } }
            compute("behov") { _, _ -> json.path(NeedKey).map(JsonNode::asText).takeUnless(List<*>::isEmpty) }
        }.toMap()

    fun withValidation(validation: MessageValidation) {
        recognizedKeys.putAll(validation.validatedKeys(json, problems).associateWith { key -> node(key) })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key.toSet() should exist })"))
    fun requireKey(vararg keys: String) {
        withValidation(validate {
            keys.toSet() should exist
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should be(value) })"))
    fun requireValue(key: String, value: Boolean) {
        withValidation(validate {
            key should be(value)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should be(value) })"))
    fun requireValue(key: String, value: String) {
        withValidation(validate {
            key should be(value)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should be(value) })"))
        fun requireValue(key: String, value: Number) {
        withValidation(validate {
            key should be(value)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should be(values) })"))
        fun requireAny(key: String, values: List<String>) {
        withValidation(validate {
            key should be(values)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River")
    fun requireArray(key: String, elementsValidation: (JsonMessage.() -> Unit)? = null) {
        val node = node(key)
        if (node.isMissingNode) return problems.error("Missing required key $key")
        if (!node.isArray) return problems.error("Required $key is not an array")
        elementsValidation?.also {
            node.forEachIndexed { index, element ->
                val elementJson = element.toString()
                val elementProblems = MessageProblems(elementJson)
                JsonMessage(elementJson, elementProblems, metrics).apply(elementsValidation)
                if (elementProblems.hasErrors()) problems.error("Array element #$index at $key did not pass validation: $elementProblems")
            }
        }
        if (!problems.hasErrors()) accessor(key)
    }

    @Deprecated("Bruk validate-dsl'en på River",
        ReplaceWith("withValidation(validate { key should beAll(listOf(value)) })")
    )
    fun requireContains(key: String, value: String) {
        withValidation(validate {
            key should beAll(listOf(value))
        })
    }

    @Deprecated("Bruk validate-dsl'en på River",
        ReplaceWith("withValidation(validate { key should beAllOrAny(values) })")
    )
    fun requireAllOrAny(key: String, values: List<String>) {
        withValidation(validate {
            key should beAllOrAny(values)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should beAll(values) })"))
    fun requireAll(key: String, values: List<String>) {
        withValidation(validate {
            key should beAll(values)
        })
    }

    fun requireAll(key: String, vararg values: Enum<*>) {
        requireAll(key, values.map(Enum<*>::name))
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should be(parser) })"))
    fun require(key: String, parser: (JsonNode) -> Any) {
        withValidation(validate {
            key should be(parser)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River")
    fun forbid(vararg key: String) {
        key.forEach { forbid(it) }
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should notBe(values) })"))
    fun forbidValues(key: String, values: List<String>) {
        withValidation(validate {
            key should notBe(values)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key.toSet() can exist })"))
    fun interestedIn(vararg key: String) {
        withValidation(validate {
            key.toSet() can exist
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key can be(parser) })"))
    fun interestedIn(key: String, parser: (JsonNode) -> Any) {
        withValidation(validate {
            key can be(parser)
        })
    }

    @Deprecated("Bruk validate-dsl'en på River", ReplaceWith("withValidation(validate { key should notExist })"))
    fun forbid(key: String) {
        withValidation(validate {
            key should notExist
        })
    }

    private fun accessor(key: String) {
        val eventName = json.path(EventNameKey).asText().takeUnless { it.isBlank() } ?: "unknown_event"
        Counter.builder("message_keys_counter")
            .description("Hvilke nøkler som er i bruk")
            .tag("event_name", eventName)
            .tag("accessor_key", key)
            .register(metrics)
            .increment()

        recognizedKeys.computeIfAbsent(key) { node(key) }
    }

    private fun node(path: String): JsonNode {
        if (!path.contains(nestedKeySeparator)) return json.path(path)
        return path.split(nestedKeySeparator).fold(json) { result: JsonNode, key ->
            result.path(key)
        }
    }

    operator fun get(key: String): JsonNode =
        requireNotNull(recognizedKeys[key]) { "$key is unknown; keys must be declared as required, forbidden, or interesting" }

    operator fun set(key: String, value: Any) {
        json.replace(key, objectMapper.valueToTree<JsonNode>(value).also {
            recognizedKeys[key] = it
        })
    }

    fun toJson(): String = objectMapper.writeValueAsString(json)
}

fun String.toUUID(): UUID = UUID.fromString(this)

fun JsonNode.isMissingOrNull() = isMissingNode || isNull

fun JsonNode.asLocalDate(): LocalDate =
    asText().let { LocalDate.parse(it) }

fun JsonNode.asYearMonth(): YearMonth =
    asText().let { YearMonth.parse(it) }

fun JsonNode.asOptionalLocalDate() =
    takeIf(JsonNode::isTextual)
        ?.asText()
        ?.takeIf(String::isNotEmpty)
        ?.let { LocalDate.parse(it) }

fun JsonNode.asOptionalLocalDateTime() =
    takeIf(JsonNode::isTextual)
        ?.asText()
        ?.takeIf(String::isNotEmpty)
        ?.let { LocalDateTime.parse(it) }

fun JsonNode.asLocalDateTime(): LocalDateTime =
    asText().let { LocalDateTime.parse(it) }
