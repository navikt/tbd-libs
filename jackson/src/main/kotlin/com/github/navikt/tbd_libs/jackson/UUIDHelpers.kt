package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

/**
 * Utility function for parsing a UUID from a Jackson JsonNode.
 *
 * @receiver [JsonNode] to parse
 *
 * @return [UUID]
 *
 * @throws IllegalArgumentException if the receiver is null, missing, null node or not textual
 */
fun JsonNode.asUUID(): UUID = this.parse(UUID::fromString)

/**
 * Utility function for parsing a UUID from a Jackson JsonNode.
 *
 * * @receiver [JsonNode] to parse
 *
 * @return [UUID], or null if the receiver is null missing or null node
 */
fun JsonNode?.asUUIDOrNull(): UUID? = this.parseOrNull(UUID::fromString)
