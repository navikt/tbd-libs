package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.apache.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.URI

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

    @Test
    fun `problem json`() {
        testApp(
            applicationModule = {
                routing {
                    get("/problem") {
                        throw RuntimeException("Denne feilen forventet DU IKKE")
                    }
                }
            }
        ) {
            val response = get("/problem")
            val body = response.body<FeilResponse>()
            assertEquals(ContentType.Application.ProblemJson, response.contentType())
            assertEquals(URI("urn:error:internal_error"), body.type)
            assertEquals(HttpStatusCode.InternalServerError.description, body.title)
            assertEquals(HttpStatusCode.InternalServerError.value, body.status)
            assertEquals(URI("/problem"), body.instance)
            assertEquals("Uventet feil: Denne feilen forventet DU IKKE", body.detail)
        }
    }

    private fun testApp(naisEndpoints: NaisEndpoints = NaisEndpoints.Default, applicationModule: Application.() -> Unit = {}, testBlock: suspend HttpClient.() -> Unit) {
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
                applicationModule()
            }
            startApplication()

            val testClient = createClient {
                defaultRequest {
                    port = randomPort
                }
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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