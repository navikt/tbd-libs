package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

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
    fun `ready check`() {
        val ready = AtomicBoolean(true)
        testApp(readyCheck = ready::get) {
            assertEquals("READY", get("/isready").bodyAsText())
            ready.set(false)
            val body = get("/isready").body<FeilResponse>()
            assertEquals(URI("urn:error:service_unavailable"), body.type)
            assertEquals(HttpStatusCode.ServiceUnavailable.description, body.title)
            assertEquals(HttpStatusCode.ServiceUnavailable.value, body.status)
            assertEquals(URI("/isready"), body.instance)
            assertEquals("Service Unavailable", body.detail)
        }
    }

    @Test
    fun `alive check`() {
        val alive = AtomicBoolean(true)
        testApp(aliveCheck = alive::get) {
            assertEquals("ALIVE", get("/isalive").bodyAsText())
            alive.set(false)
            val body = get("/isalive").body<FeilResponse>()
            assertEquals(URI("urn:error:service_unavailable"), body.type)
            assertEquals(HttpStatusCode.ServiceUnavailable.description, body.title)
            assertEquals(HttpStatusCode.ServiceUnavailable.value, body.status)
            assertEquals(URI("/isalive"), body.instance)
            assertEquals("Service Unavailable", body.detail)
        }
    }

    @Test
    fun `custom routes`() {
        val endpoints = NaisEndpoints(
            isaliveEndpoint = "/erILive",
            isreadyEndpoint = "/erKlar",
            metricsEndpoint = "/metrikker",
            preStopEndpoint = "/stopp",
        )
        testApp(endpoints) {
            assertEquals("ALIVE", get(endpoints.isaliveEndpoint).bodyAsText())
            assertEquals("READY", get(endpoints.isreadyEndpoint).bodyAsText())
            assertTrue(get(endpoints.metricsEndpoint).bodyAsText().contains("jvm_memory_used_bytes"))
        }
    }

    @Test
    fun `shutdown hook`() {
        testApp {
            val stopRequest = async(Dispatchers.IO) {
                get("/stop")
            }
            val maxWait = Duration.ofSeconds(5)
            assertUntil(maxWait) {
                assertDoesNotThrow {
                    val body = get("/isready").body<FeilResponse>()
                    assertEquals(URI("urn:error:service_unavailable"), body.type)
                    assertEquals(HttpStatusCode.ServiceUnavailable.description, body.title)
                    assertEquals(HttpStatusCode.ServiceUnavailable.value, body.status)
                    assertEquals(URI("/isready"), body.instance)
                    assertEquals("Service Unavailable", body.detail)
                }
            }
            val response = stopRequest.await()
            assertTrue(response.status.isSuccess())
        }
    }

    @Test
    fun `method not allowed`() {
        testApp(
            applicationModule = {
                routing {
                    get("/test") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        ) {
            val response = post("/test")
            val body = response.body<FeilResponse>()
            assertEquals(ContentType.Application.ProblemJson, response.contentType())
            assertEquals(URI("urn:error:method_not_allowed"), body.type)
            assertEquals(HttpStatusCode.MethodNotAllowed.description, body.title)
            assertEquals(HttpStatusCode.MethodNotAllowed.value, body.status)
            assertEquals(URI("/test"), body.instance)
            assertEquals("Method Not Allowed", body.detail)
        }
    }

    private suspend fun assertUntil(maxWait: Duration, assertion: suspend () -> Unit) {
        val startTime = System.currentTimeMillis()
        lateinit var lastAssertionError: AssertionError
        while ((System.currentTimeMillis() - startTime) < maxWait.toMillis()) {
            try {
                return assertion()
            } catch (err: AssertionError) {
                lastAssertionError = err
            }
        }
        throw lastAssertionError
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

    private fun testApp(
        naisEndpoints: NaisEndpoints = NaisEndpoints.Default,
        aliveCheck: () -> Boolean = { true },
        readyCheck: () -> Boolean = { true },
        applicationModule: Application.() -> Unit = {},
        testBlock: suspend HttpClient.() -> Unit
    ) {
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
                standardApiModule(
                    meterRegistry = meterRegistry,
                    objectMapper = objectMapper,
                    callLogger = environment.log,
                    naisEndpoints = naisEndpoints,
                    callIdHeaderName = "callId",
                    preStopHook = { delay(250) },
                    aliveCheck = aliveCheck,
                    readyCheck = readyCheck
                )
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