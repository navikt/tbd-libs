package com.github.navikt.tbd_libs.jackson_helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate

private const val notObjectNodeError = "Receiver must be ObjectNode. This function should be called on the parent of the field you want to parse"
/**
 * Utility function for parsing a LocalDate from a Jackson JsonNode.
 *
 * This function should be called on the parent object of the field that is to be parsed.
 *
 * @param field The field to parse from the receiver JsonNode
 *
 * @return [LocalDate]
 *
 * @throws IllegalArgumentException if the receiver is not an ObjectNode or if the ObjectNode does not contain the given [field]
 */
fun JsonNode.asLocalDate(field: String): LocalDate {
    require(this is ObjectNode) { notObjectNodeError }
    require(this.has(field)) { "\"$field\" must be a property of the json object" }
    return LocalDate.parse(this.get(field).asText())
}

/**
 * Utility function for parsing a LocalDate from a Jackson JsonNode.
 *
 * This function should be called on the parent object of the field that is to be parsed.
 *
 * @param field The field to parse from the receiver JsonNode
 *
 * @return [LocalDate], or null if the field is missing from the parent node or if the value of the field is null
 */
fun JsonNode.asLocalDateOrNull(field: String): LocalDate? {
    require(this is ObjectNode) { notObjectNodeError }
    if (this.path(field).isNull or this.path(field).isMissingNode) return null
    return LocalDate.parse(this.get(field).asText())
}
