package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.Logger
import org.slf4j.event.Level
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.String

data class NaisEndpoints(
    val isaliveEndpoint: String,
    val isreadyEndpoint: String,
    val metricsEndpoint: String
) {
    companion object {
        val Default = NaisEndpoints(
            isaliveEndpoint = "/isalive",
            isreadyEndpoint = "/isready",
            metricsEndpoint = "/metrics"
        )
    }
}

fun naisApp(
    meterRegistry: PrometheusMeterRegistry,
    objectMapper: ObjectMapper,
    applicationLogger: Logger,
    callLogger: Logger,
    naisEndpoints: NaisEndpoints = NaisEndpoints.Default,
    callIdHeaderName: String = "callId",
    port: Int = 8080,
    applicationModule: Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val config = serverConfig(
        environment = applicationEnvironment {
            log = applicationLogger
        }
    ) {
        module { standardApiModule(meterRegistry, objectMapper, callLogger, naisEndpoints, callIdHeaderName) }
        module(applicationModule)
    }
    val app = EmbeddedServer(config, CIO) {
        connectors.add(EngineConnectorBuilder().apply {
            this.port = port
        })
    }
    return app
}

fun Application.standardApiModule(
    meterRegistry: PrometheusMeterRegistry,
    objectMapper: ObjectMapper,
    callLogger: Logger,
    naisEndpoints: NaisEndpoints,
    callIdHeaderName: String
) {
    val readyToggle = AtomicBoolean(false)
    monitor.subscribe(ApplicationStarted) {
        readyToggle.set(true)
    }
    install(CallId) {
        header(callIdHeaderName)
        verify { it.isNotEmpty() }
        generate { UUID.randomUUID().toString() }
    }
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
    install(CallLogging) {
        logger = callLogger
        level = Level.INFO
        callIdMdc(callIdHeaderName)
        disableDefaultColors()

        val ignoredPaths = setOf(
            naisEndpoints.metricsEndpoint,
            naisEndpoints.isreadyEndpoint,
            naisEndpoints.isaliveEndpoint
        )
        filter { call ->
            ignoredPaths.none { ignoredPath ->
                call.request.path().startsWith(ignoredPath)
            }
        }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, FeilResponse(
                feilmelding = "Ugyldig request: ${cause.message}",
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, FeilResponse(
                feilmelding = "Ikke funnet: ${cause.message}",
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
        exception<Throwable> { call, cause ->
            call.application.log.info("ukjent feil: ${cause.message}. svarer med InternalServerError og en feilmelding i JSON", cause)
            call.respond(HttpStatusCode.InternalServerError, FeilResponse(
                feilmelding = "Tjeneren møtte på en uventet feil: ${cause.message}",
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
    }
    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
        )
    }
    routing {
        get(naisEndpoints.isaliveEndpoint) {
            call.respondText("ALIVE", ContentType.Text.Plain)
        }

        get(naisEndpoints.isreadyEndpoint) {
            if (!readyToggle.get()) return@get call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
            call.respondText("READY", ContentType.Text.Plain)
        }

        get(naisEndpoints.metricsEndpoint) {
            call.respond(meterRegistry.scrape())
        }
    }
}

data class FeilResponse(
    val feilmelding: String,
    val callId: String?,
    val stacktrace: String? = null
)