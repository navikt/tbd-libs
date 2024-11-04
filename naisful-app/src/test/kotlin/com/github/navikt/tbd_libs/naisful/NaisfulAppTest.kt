package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.server.engine.connector
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.ServerSocket

class NaisfulAppTest {

    @Test
    fun test() {
        testApp(NaisEndpoints.Default) {
            assertEquals("ALIVE", get("/isalive").bodyAsText())
            assertEquals("READY", get("/isready").bodyAsText())
            assertTrue(get("/metrics").bodyAsText().contains("jvm_memory_used_bytes"))
        }
    }

    @Test
    fun `custom routes`() {
        val endpoints = NaisEndpoints(
            isaliveEndpoint = "/erILive",
            isreadyEndpoint = "/erKlar",
            metricsEndpoint = "/metrikker"
        )
        testApp(endpoints) {
            assertEquals("ALIVE", get(endpoints.isaliveEndpoint).bodyAsText())
            assertEquals("READY", get(endpoints.isreadyEndpoint).bodyAsText())
            assertTrue(get(endpoints.metricsEndpoint).bodyAsText().contains("jvm_memory_used_bytes"))
        }
    }

    private fun testApp(naisEndpoints: NaisEndpoints, testBlock: suspend HttpClient.() -> Unit) {
        val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val objectMapper = jacksonObjectMapper()
        val randomPort = ServerSocket(0).localPort

        testApplication {
            environment {
                this.log = LoggerFactory.getLogger(this::class.java)
            }
            engine {
                connector {
                    host = "localhost"
                    port = randomPort
                }
            }
            application {
                standardApiModule(meterRegistry, objectMapper, environment.log, naisEndpoints, "callId")
            }
            startApplication()

            val testClient = createClient {
                defaultRequest {
                    port = randomPort
                }
            }

            do {
                val response = testClient.get(naisEndpoints.isreadyEndpoint)
                println("Venter på at isready svarer OK…:${response.status}")
            } while (!response.status.isSuccess())

            testBlock(testClient)
        }
    }
}