package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONObject

internal object Jackson: JsonSerde {
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())
    override fun deserialize(content: String): Map<String, Any?> = objectMapper.readValue(content)
    override fun serialize(content: Map<String, Any?>): String = objectMapper.writeValueAsString(content)
}

internal object OrgJson: JsonSerde {
    override fun deserialize(content: String): Map<String, Any?> = JSONObject(content).toMap()
    override fun serialize(content: Map<String, Any?>) = JSONObject(content).toString()
}