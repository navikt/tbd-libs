package com.github.navikt.tbd_libs.jackson

import com.fasterxml.jackson.databind.JsonNode

internal fun <T> JsonNode?.parse(parser: (String) -> T): T {
    return requireNotNull(parseOrNull(parser)) { "Receiver must be a non-null textual property of the parent json object. Receiver is ${this?.nodeType}" }
}

internal fun <T> JsonNode?.parseOrNull(parser: (String) -> T): T? {
    if (this == null || !this.isTextual) return null
    return parser(this.asText())
}
