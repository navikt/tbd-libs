package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.tracer.common.SpanContext
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory

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
    fun exemplars() {
        val exemplarSamler = ExemplarSampler()
        val meterRegistry = PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT,
            PrometheusRegistry.defaultRegistry,
            Clock.SYSTEM,
            exemplarSamler
        )
        testApp(NaisEndpoints.Default, meterRegistry) {
            assertEquals("ALIVE", get("/isalive").bodyAsText())
            assertEquals("READY", get("/isready").bodyAsText())

            get("/metrics") {
                accept(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            }
                .bodyAsText()
                .also { body ->
                    assertTrue(body.contains("jvm_memory_used_bytes"))
                    assertFalse(body.contains("my_trace_id"))
                }

            get("/metrics") {
                accept(ContentType.parse("application/openmetrics-text; version=1.0.0"))
            }
                .bodyAsText()
                .also { body ->
                    assertTrue(body.contains("jvm_memory_used_bytes"))
                    assertTrue(body.contains("my_trace_id"))
                }
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
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `default error body`() {
        testApp(
            applicationModule = {
                routing {
                    get("/test1") {
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/test2") {
                        call.respond(HttpStatusCode.Conflict, "This is a body")
                    }
                    get("/test3") {
                        call.respond(HttpStatusCode.Forbidden, CustomResponse("Fault"))
                    }
                }
            }
        ) {
            post("/test1").also { response ->
                val body = response.body<FeilResponse>()
                assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
                assertEquals(ContentType.Application.ProblemJson, response.contentType())
                assertEquals(URI("urn:error:method_not_allowed"), body.type)
                assertEquals(HttpStatusCode.MethodNotAllowed.description, body.title)
                assertEquals(HttpStatusCode.MethodNotAllowed.value, body.status)
                assertEquals(URI("/test1"), body.instance)
                assertEquals("Method Not Allowed", body.detail)
            }
            get("/test2").also { response ->
                assertEquals(HttpStatusCode.Conflict, response.status)
                assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), response.contentType())
                assertEquals("This is a body", response.bodyAsText())
            }
            get("/test3").also { response ->
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
                assertEquals("Fault", response.body<CustomResponse>().text)
            }
        }
    }

    private data class CustomResponse(val text: String)

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
        meterRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
        aliveCheck: () -> Boolean = { true },
        readyCheck: () -> Boolean = { true },
        applicationModule: Application.() -> Unit = {},
        testBlock: suspend HttpClient.() -> Unit
    ) {
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

private class ExemplarSampler : SpanContext {
    override fun getCurrentTraceId() = "my_trace_id"

    override fun getCurrentSpanId() = "my_span_id"

    override fun isCurrentSpanSampled() = true

    override fun markCurrentSpanAsExemplar() {}
}
