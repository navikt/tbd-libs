plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(project(":kafka"))
    api(project(":rapids-and-rivers-api"))

    api(platform(libs.jackson3.bom))
    api("tools.jackson.module:jackson-module-kotlin")

    implementation(libs.opentelemetry.instrumentation.annotations)

    testImplementation(project(":rapids-and-rivers-test"))
    testImplementation(project(":kafka-test"))
    testImplementation(libs.awaitility)
    testImplementation(libs.kotlinx.coroutines)

    testImplementation(libs.logback.classic)
    testImplementation(libs.logstash.logback.encoder)
}
