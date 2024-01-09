package com.github.navikt.tbd_libs.azure

import java.lang.Exception

interface JsonSerde {
    fun deserialize(content: String): Map<String, Any?>
    fun serialize(content: Map<String, Any?>): String

    companion object {
        internal fun Map<String, Any?>.stringOrNull(key: String): String? = get(key)?.takeIf { it is String }?.let { it as String }
        internal fun Map<String, Any?>.longOrNull(key: String): Long? = get(key)?.takeIf { it is Number }?.let { it as Number }?.toLong()
        internal fun JsonSerde.deserializeOrNull(content: String) = try { deserialize(content) } catch (_: Exception) { null }
    }
}