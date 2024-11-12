val jacksonVersion: String by project
val kotlinxCoroutinesVersion: String by project
val awaitilityVersion = "4.2.2"
val otelVersion = "2.9.0"

dependencies {
    api(project(":kafka"))
    api(project(":rapids-and-rivers-api"))

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$otelVersion")

    testImplementation(project(":rapids-and-rivers-test"))
    testImplementation(project(":kafka-test"))
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    testImplementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation("net.logstash.logback:logstash-logback-encoder:8.0")
}