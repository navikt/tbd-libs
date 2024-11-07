package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Utility function for parsing a LocalDateTime from a Jackson JsonNode.
 *
 * @receiver [JsonNode] to parse
 *
 * @return [LocalDateTime]
 *
 * @throws IllegalArgumentException if the receiver is null, missing, null node or not textual
 */
fun JsonNode.asLocalDateTime(): LocalDateTime = this.parse(LocalDateTime::parse)

/**
 * Utility function for parsing a LocalDateTime from a Jackson JsonNode.
 *
 * * @receiver [JsonNode] to parse
 *
 * @return [LocalDateTime], or null if the receiver is null missing or null node
 */
fun JsonNode?.asLocalDateTimeOrNull(): LocalDateTime? = this.parseOrNull(LocalDateTime::parse)
