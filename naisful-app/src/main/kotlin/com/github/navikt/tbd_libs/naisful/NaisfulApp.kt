package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.Logger
import org.slf4j.event.Level
import java.net.URI
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
    timersConfig: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> },
    mdcEntries: Map<String, (ApplicationCall) -> String?> = emptyMap(),
    port: Int = 8080,
    applicationModule: Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val config = serverConfig(
        environment = applicationEnvironment {
            log = applicationLogger
        }
    ) {
        module { standardApiModule(meterRegistry, objectMapper, callLogger, naisEndpoints, callIdHeaderName, timersConfig, mdcEntries) }
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
    callIdHeaderName: String,
    timersConfig: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> },
    mdcEntries: Map<String, (ApplicationCall) -> String?> = emptyMap(),
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
        mdcEntries.forEach { (k, v) -> mdc(k, v)}
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
            call.response.header("Content-Type", ContentType.Application.ProblemJson.toString())
            call.respond(HttpStatusCode.BadRequest, FeilResponse(
                status = HttpStatusCode.BadRequest,
                type = URI("urn:error:bad_request"),
                detail = cause.message,
                instance = URI(call.request.uri),
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
        exception<NotFoundException> { call, cause ->
            call.response.header("Content-Type", ContentType.Application.ProblemJson.toString())
            call.respond(HttpStatusCode.NotFound, FeilResponse(
                status = HttpStatusCode.NotFound,
                type = URI("urn:error:not_found"),
                detail = cause.message,
                instance = URI(call.request.uri),
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
        exception<Throwable> { call, cause ->
            call.application.log.info("ukjent feil: ${cause.message}. svarer med InternalServerError og en feilmelding i JSON", cause)
            call.response.header("Content-Type", ContentType.Application.ProblemJson.toString())
            call.respond(HttpStatusCode.InternalServerError, FeilResponse(
                status = HttpStatusCode.InternalServerError,
                type = URI("urn:error:internal_error"),
                detail = "Uventet feil: ${cause.message}",
                instance = URI(call.request.uri),
                callId = call.callId,
                stacktrace = cause.stackTraceToString()
            ))
        }
    }
    install(MicrometerMetrics) {
        registry = meterRegistry
        timers(timersConfig)
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

// implementerer Problem Details for HTTP APIs
// https://www.rfc-editor.org/rfc/rfc9457.html
data class FeilResponse(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: URI,
    val callId: String?,
    val stacktrace: String? = null
) {
    constructor(
        status: HttpStatusCode,
        type: URI,
        detail: String?,
        instance: URI,
        callId: String?,
        stacktrace: String? = null
    ) : this(type, status.description, status.value, detail, instance, callId, stacktrace)
}