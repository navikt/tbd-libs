plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(libs.slf4j.api)

    api(libs.ktor.server.cio)
    api(libs.ktor.server.call.id)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.serialization.jackson3)

    api(libs.ktor.server.metrics.micrometer)
    api(libs.micrometer.registry.prometheus)

    testImplementation(libs.ktor.server.test.host) {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
    testImplementation(libs.ktor.client.content.negotiation)
}
