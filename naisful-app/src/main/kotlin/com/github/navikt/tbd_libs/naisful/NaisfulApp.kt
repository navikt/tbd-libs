package com.github.navikt.tbd_libs.naisful

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.allStatusCodes
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.event.Level
import java.net.URI
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    connector {
        this.port = port
    }
}

fun plainApp(
    applicationLogger: Logger,
    port: Int = 8080,
    developmentMode: Boolean = defaultDevelopmentMode(),
    gracefulShutdownDelay: Duration = 20.seconds,
    cioConfiguration: CIOApplicationEngine.Configuration.() -> Unit = { },
    applicationModule: Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val config = serverConfig(
        environment = applicationEnvironment {
            log = applicationLogger
        }
    ) {
        this.developmentMode = developmentMode
        module {
            monitor.subscribe(ApplicationStarting) { it.log.info("Application starting …") }
            monitor.subscribe(ApplicationStarted) { it.log.info("Application started …") }
            monitor.subscribe(ServerReady) { it.log.info("Application ready …") }
            monitor.subscribe(ApplicationStopPreparing) { it.log.info("Application preparing to stop …") }
            monitor.subscribe(ApplicationStopping) { it.log.info("Application stopping …") }
            monitor.subscribe(ApplicationStopped) { it.log.info("Application stopped …") }

            applicationModule()
        }
    }
    val cioConfig = defaultCIOConfiguration(port).apply(cioConfiguration)
    val app = EmbeddedServer(config, CIO) {
        takeFrom(cioConfig)
    }

    val hook = ShutdownHook(app, gracefulShutdownDelay)
    Runtime.getRuntime().addShutdownHook(hook)
    app.monitor.subscribe(ApplicationStopping) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (_: IllegalStateException) {
            // ignore
        }
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
    gracefulShutdownDelay: Duration = 20.seconds,
    applicationModule: Application.() -> Unit
) = plainApp(
    applicationLogger = applicationLogger,
    port = port,
    developmentMode = developmentMode,
    gracefulShutdownDelay = gracefulShutdownDelay,
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
        val defaultBinders = listOf(
            ClassLoaderMetrics(),
            JvmInfoMetrics(),
            JvmMemoryMetrics(),
            JvmThreadMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics()
        )
        meterBinders = defaultBinders + buildList {
            try {
                Class.forName("ch.qos.logback.classic.LoggerContext")
                add(LogbackMetrics())
            } catch (_: ClassNotFoundException) {}
        }
    }

    with(meterRegistry) {
        val pkg = ::naisApp.javaClass.`package`
        val vendor = pkg?.implementationVendor ?: "unknown"
        val version = pkg?.implementationVersion ?: "unknown"
        MultiGauge.builder("naisful.info")
            .description("Naisful version info")
            .tag("vendor", vendor)
            .tag("version", version)
            .register(this)
            .register(listOf(MultiGauge.Row.of(Tags.of(emptyList()), 1)))
    }

    val readyToggle = AtomicBoolean(false)
    monitor.subscribe(ApplicationStarted) {
        readyToggle.set(true)
    }
    monitor.subscribe(ApplicationStopPreparing) {
        readyToggle.set(false)
    }

    routing {
        /*
            https://doc.nais.io/workloads/explanations/good-practices/?h=graceful#handles-termination-gracefully

            termination lifecycle:
                1. Application (pod) gets status TERMINATING, and grace period starts (default 30s)
                    (simultaneous with 1) If the pod has a preStop hook defined, this is invoked
                    (simultaneous with 1) The pod is removed from the list of endpoints i.e. taken out of load balancing
                    (simultaneous with 1, but after preStop if defined) Container receives SIGTERM, and should prepare for shutdown
                2. Grace period ends, and container receives SIGKILL
                3. Pod disappears from the API, and is no longer visible for the client.
         */
        get(naisEndpoints.preStopEndpoint) {
            application.log.info("Received shutdown signal via preStopHookPath, calling actual stop hook")
            application.monitor.raise(ApplicationStopPreparing, environment)

            /**
             *  fra doccen:
             *  Be aware that even after your preStop-hook has been triggered,
             *  your application might still receive new connections for a few seconds.
             *  This is because step 3 above can take a few seconds to complete.
             *  Your application should handle those connections before exiting.
             */
            preStopHook()

            application.log.info("Stop hook returned. Responding to preStopHook request with 200 OK")
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
    // venter by default i 5 sekunder før pre-stop-hook-endepunktet svarer
    // i praksis betyr det at det går 5 sekunder før appen mottar SIGTERM.
    delay(5.seconds)
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

private data class ShutdownHook(
    val app: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
    val gracefulShutdownDelay: Duration
) : Thread("naisful-app-shutdown-hook") {
    init {
        // deaktiverer ktors egen shutdown hook fordi den ikke er så graceful!
        System.setProperty("io.ktor.server.engine.ShutdownHook", "false")
    }

    override fun run() {
        app.apply {
            environment.log.info("Shut down hook called. waiting $gracefulShutdownDelay before disposing application.")
            monitor.raiseCatching(ApplicationStopPreparing, environment, environment.log)
            runBlocking { delay(gracefulShutdownDelay) }
            environment.log.info("Disposing application")
            application.dispose()
        }
    }
}