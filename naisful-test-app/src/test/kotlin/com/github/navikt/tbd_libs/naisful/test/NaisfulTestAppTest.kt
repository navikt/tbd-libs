package com.github.navikt.tbd_libs.naisful.test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.NaisEndpoints
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaisfulTestAppTest {

    @Test
    fun `nais endpoints`() {
        val endpoints = NaisEndpoints(
            isaliveEndpoint = "/erILive",
            isreadyEndpoint = "/erKlar",
            metricsEndpoint = "/metrikker",
            preStopEndpoint = "/stopp",
        )
        naisfulTestApp({}, jacksonObjectMapper(), PrometheusMeterRegistry(PrometheusConfig.DEFAULT), endpoints) {
            assertEquals("ALIVE", client.get(endpoints.isaliveEndpoint).bodyAsText())
            assertEquals("READY", client.get(endpoints.isreadyEndpoint).bodyAsText())
            assertTrue(client.get(endpoints.metricsEndpoint).bodyAsText().contains("jvm_memory_used_bytes"))
        }
    }
}