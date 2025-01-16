val slf4jVersion = "2.0.16"
val ktorVersion = "3.0.3"
val micrometerRegistryPrometheusVersion = "1.14.3"

dependencies {
    api("org.slf4j:slf4j-api:$slf4jVersion")

    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-server-call-id:$ktorVersion")
    api("io.ktor:ktor-server-status-pages:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")

    api("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    api("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}
