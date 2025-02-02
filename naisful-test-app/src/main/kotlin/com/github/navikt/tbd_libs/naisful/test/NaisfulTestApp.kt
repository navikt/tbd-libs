package com.github.navikt.tbd_libs.naisful.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.naisful.NaisEndpoints
import com.github.navikt.tbd_libs.naisful.standardApiModule
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.net.ServerSocket

fun plainTestApp(
    testApplicationModule: Application.() -> Unit,
    isreadyEndpoint: String = NaisEndpoints.Default.isreadyEndpoint,
    testClientObjectMapper: ObjectMapper = jacksonObjectMapper(),
    testblokk: suspend TestContext.() -> Unit,
) {
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
            testApplicationModule()
        }
        startApplication()

        val testClient = createClient {
            defaultRequest {
                port = randomPort
            }
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(testClientObjectMapper))
            }
        }

        do {
            val response = testClient.get(isreadyEndpoint)
            println("Venter på at $isreadyEndpoint svarer OK…:${response.status}")
        } while (!response.status.isSuccess())

        testblokk(TestContext(testClient))
    }
}

fun naisfulTestApp(
    testApplicationModule: Application.() -> Unit,
    objectMapper: ObjectMapper,
    meterRegistry: PrometheusMeterRegistry,
    naisEndpoints: NaisEndpoints = NaisEndpoints.Default,
    callIdHeaderName: String = "callId",
    preStopHook: suspend () -> Unit = { delay(5000) },
    testblokk: suspend TestContext.() -> Unit
) = plainTestApp(
    testApplicationModule = {
        standardApiModule(meterRegistry, objectMapper, environment.log, naisEndpoints, callIdHeaderName, preStopHook)
        testApplicationModule()
    },
    isreadyEndpoint = naisEndpoints.isreadyEndpoint,
    testClientObjectMapper = objectMapper,
) { testblokk() }

class TestContext(val client: HttpClient)