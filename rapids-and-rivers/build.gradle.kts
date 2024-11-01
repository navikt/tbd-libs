val jacksonVersion: String by project
val testcontainersVersion: String by project
val awaitilityVersion = "4.2.2"
val otelVersion = "2.9.0"

dependencies {
    api(project(":kafka"))
    api(project(":rapids-and-rivers-api"))

    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$otelVersion")

    testImplementation(project(":rapids-and-rivers-test"))
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC.2")
}