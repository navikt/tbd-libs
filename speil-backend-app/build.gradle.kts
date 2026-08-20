plugins {
    alias(libs.plugins.kotlin.plugin.serialization)
    `java-test-fixtures`
}

// AuditloggerTest henter ut LoggerContext via LoggerFactory.getILoggerFactory() rett i @BeforeEach.
// Root-konfigurasjonen kjører JUnit5-tester parallelt (fixed strategy), noe som race'er mot SLF4Js
// binder-initialisering (LoggerFactory returnerer da en midlertidig SubstituteLoggerFactory).
// Kjør derfor testene i denne modulen sekvensielt.
tasks.test {
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.call.id)
    api(libs.ktor.server.call.logging)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.serialization.jackson3)
    api(libs.ktor.server.metrics.micrometer)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.resources)
    api(libs.kotlinx.serialization.json)
    implementation(libs.bundles.smiley4.ktor.openapi.tools)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.hikariCP)
    api(libs.postgresql)
    implementation(libs.flyway.postgresql)

    api(project(":naisful-postgres"))
    api(project(":access-token-provider-api"))
    implementation(project(":access-token-provider-texas"))
    api(project(":person-pseudo-id"))
    api(project(":populasjonstilgangskontroll-provider-api"))
    implementation(project(":populasjonstilgangskontroll-provider-tilgangsmaskinen"))
    api(libs.rapids.and.rivers)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.wiremock)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.postgresql)

    testFixturesImplementation(libs.mock.oauth2.server)
    testFixturesImplementation(libs.wiremock)
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.ktor.server.core)
    testFixturesImplementation(libs.ktor.server.cio)
    testFixturesImplementation(libs.hikariCP)
    testFixturesImplementation(libs.flyway.postgresql)
    testFixturesImplementation(libs.postgresql)
}
