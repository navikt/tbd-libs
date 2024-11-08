package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode
import java.time.YearMonth

/**
 * Utility function for parsing a YearMonth from a Jackson JsonNode.
 *
 * @receiver [JsonNode] to parse
 *
 * @return [YearMonth]
 *
 * @throws IllegalArgumentException if the receiver is null, missing, null node or not textual
 */
fun JsonNode.asYearMonth(): YearMonth = this.parse(YearMonth::parse)

/**
 * Utility function for parsing a YearMonth from a Jackson JsonNode.
 *
 * * @receiver [JsonNode] to parse
 *
 * @return [YearMonth], or null if the receiver is null missing or null node
 */
fun JsonNode?.asYearMonthOrNull(): YearMonth? = this.parseOrNull(YearMonth::parse)
