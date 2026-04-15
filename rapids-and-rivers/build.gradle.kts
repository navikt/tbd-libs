dependencies {
    api(project(":kafka"))
    api(project(":rapids-and-rivers-api"))

    api(platform(libs.jackson2.bom))
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation(libs.opentelemetry.instrumentation.annotations)

    testImplementation(project(":rapids-and-rivers-test"))
    testImplementation(project(":kafka-test"))
    testImplementation(libs.awaitility)
    testImplementation(libs.kotlinx.coroutines)

    testImplementation(libs.logback.classic)
    testImplementation(libs.logstash.logback.encoder)
}
