package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.allStatusCodes
import io.ktor.http.content.isEmpty
import io.ktor.http.isSuccess
import io.ktor.serialization.Configuration
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
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.withPort
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.PlatformUtils
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.event.Level
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.String
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toJavaDuration

data class NaisEndpoints(
    val isaliveEndpoint: String,
    val isreadyEndpoint: String,
    val metricsEndpoint: String,
    val preStopEndpoint: String
) {
    companion object {
        val Default = NaisEndpoints(
            isaliveEndpoint = "/isalive",
            isreadyEndpoint = "/isready",
            metricsEndpoint = "/metrics",
            preStopEndpoint = "/stop"
        )
    }
}

private fun defaultCIOConfiguration(port: Int) = CIOApplicationEngine.Configuration().apply {
    shutdownGracePeriod = 30.seconds.toLong(DurationUnit.MILLISECONDS)
    shutdownTimeout = 30.seconds.toLong(DurationUnit.MILLISECONDS)
    connector {
        this.port = port
    }
}

fun plainApp(
    applicationLogger: Logger,
    port: Int = 8080,
    developmentMode: Boolean = defaultDevelopmentMode(),
    cioConfiguration: CIOApplicationEngine.Configuration.() -> Unit = { },
    applicationModule: Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val config = serverConfig(
        environment = applicationEnvironment {
            log = applicationLogger
        }
    ) {
        this.developmentMode = developmentMode
        module { applicationModule() }
    }
    val cioConfig = defaultCIOConfiguration(port).apply(cioConfiguration)
    val app = EmbeddedServer(config, CIO) {
        takeFrom(cioConfig)
    }
    return app
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
    aliveCheck: () -> Boolean = { true },
    readyCheck: () -> Boolean = { true },
    preStopHook: suspend () -> Unit = ::defaultPreStopHook,
    cioConfiguration: CIOApplicationEngine.Configuration.() -> Unit = { },
    statusPagesConfig: StatusPagesConfig.() -> Unit = { defaultStatusPagesConfig() },
    developmentMode: Boolean = defaultDevelopmentMode(),
    applicationModule: Application.() -> Unit
) = plainApp(
    applicationLogger = applicationLogger,
    port = port,
    developmentMode = developmentMode,
    cioConfiguration = cioConfiguration,
    applicationModule = {
        standardApiModule(
            meterRegistry = meterRegistry,
            objectMapper = objectMapper,
            callLogger = callLogger,
            naisEndpoints = naisEndpoints,
            callIdHeaderName = callIdHeaderName,
            preStopHook = preStopHook,
            aliveCheck = aliveCheck,
            readyCheck = readyCheck,
            timersConfig = timersConfig,
            mdcEntries = mdcEntries,
            statusPagesConfig = statusPagesConfig
        )
        applicationModule()
    }
)

fun Application.standardApiModule(
    meterRegistry: PrometheusMeterRegistry,
    objectMapper: ObjectMapper,
    callLogger: Logger,
    naisEndpoints: NaisEndpoints,
    callIdHeaderName: String,
    preStopHook: suspend () -> Unit = ::defaultPreStopHook,
    aliveCheck: () -> Boolean = { true },
    readyCheck: () -> Boolean = { true },
    timersConfig: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> },
    mdcEntries: Map<String, (ApplicationCall) -> String?> = emptyMap(),
    statusPagesConfig: StatusPagesConfig.() -> Unit = { defaultStatusPagesConfig() }
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
        statusPagesConfig()
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
        get(naisEndpoints.preStopEndpoint) {
            application.log.info("Received shutdown signal via preStopHookPath, calling actual stop hook")
            readyToggle.set(false)
            preStopHook()
            application.log.info("Stop hook returned, responding to preStopHook request with 200 OK")
            call.respond(HttpStatusCode.OK)
        }
        get(naisEndpoints.isaliveEndpoint) {
            if (!aliveCheck()) return@get call.respond(HttpStatusCode.ServiceUnavailable)
            call.respondText("ALIVE", ContentType.Text.Plain)
        }
        get(naisEndpoints.isreadyEndpoint) {
            if (!readyToggle.get() || !readyCheck()) return@get call.respond(HttpStatusCode.ServiceUnavailable)
            call.respondText("READY", ContentType.Text.Plain)
        }

        get(naisEndpoints.metricsEndpoint) {
            call.respond(meterRegistry.scrape())
        }
    }
}

internal fun defaultDevelopmentMode(): Boolean {
    val clusterName: String? = System.getenv("NAIS_CLUSTER_NAME")
    if (clusterName.isNullOrBlank()) return true
    if (clusterName == "dev-gcp") return true
    return PlatformUtils.IS_DEVELOPMENT_MODE
}

internal suspend fun defaultPreStopHook() {
    /* delayet bør være lenge nok til at:
        a) k8s har prob'et appens isready-endepunkt og stoppet videreformidling av requests
        b) alle pågående requests er ferdig

        a) vil nok være den treigeste
     */
    delay(30.seconds)
}

fun StatusPagesConfig.defaultStatusPagesConfig() {
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
    status(*allStatusCodes.filterNot { code -> code.isSuccess() }.toTypedArray()) { statusCode ->
        if (content.contentLength == null) {
            call.response.header("Content-Type", ContentType.Application.ProblemJson.toString())
            call.respond(statusCode, FeilResponse(
                status = statusCode,
                type = statusCode.toURI(call),
                detail = statusCode.description,
                instance = URI(call.request.uri),
                callId = call.callId,
                stacktrace = null
            ))
        }
    }
}

private fun HttpStatusCode.toURI(call: ApplicationCall): URI {
    val type = try {
        description.lowercase().replace("\\s+".toRegex(), "_")
    } catch (_: Exception) {
        call.application.log.error("klarte ikke lage uri fra httpstatuscode=$this")
        "unknown_error"
    }
    return URI("urn:error:$type")
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