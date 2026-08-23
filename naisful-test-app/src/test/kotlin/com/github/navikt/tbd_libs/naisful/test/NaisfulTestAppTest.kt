package com.github.navikt.tbd_libs.naisful.test

import com.github.navikt.tbd_libs.naisful.NaisEndpoints
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder

class NaisfulTestAppTest {

    @Test
    fun `nais endpoints`() {
        val endpoints = NaisEndpoints(
            isaliveEndpoint = "/erILive",
            isreadyEndpoint = "/erKlar",
            metricsEndpoint = "/metrikker",
            preStopEndpoint = "/stopp",
        )
        naisfulTestApp(
            testApplicationModule = {},
            objectMapper = jacksonMapperBuilder()
                .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
                .build(),
            meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            naisEndpoints = endpoints
        ) {
            assertEquals("ALIVE", client.get(endpoints.isaliveEndpoint).bodyAsText())
            assertEquals("READY", client.get(endpoints.isreadyEndpoint).bodyAsText())
            assertTrue(client.get(endpoints.metricsEndpoint).bodyAsText().contains("jvm_memory_used_bytes"))
        }
    }
}
