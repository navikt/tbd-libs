package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

/**
 * Utility function for parsing a LocalDate from a Jackson JsonNode.
 *
 * @receiver [JsonNode] to parse
 *
 * @return [LocalDate]
 *
 * @throws IllegalArgumentException if the receiver is null, missing, null node or not textual
 */
fun JsonNode.asLocalDate(): LocalDate = this.parse(LocalDate::parse)

/**
 * Utility function for parsing a LocalDate from a Jackson JsonNode.
 *
 * * @receiver [JsonNode] to parse
 *
 * @return [LocalDate], or null if the receiver is null missing or null node
 */
fun JsonNode?.asLocalDateOrNull(): LocalDate? = this.parseOrNull(LocalDate::parse)
