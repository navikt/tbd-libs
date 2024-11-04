val slf4jVersion = "2.0.16"
val ktorVersion = "3.0.1"
val micrometerRegistryPrometheusVersion = "1.13.6"

dependencies {
    api("org.slf4j:slf4j-api:$slf4jVersion")

    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-server-call-id:$ktorVersion")
    api("io.ktor:ktor-server-status-pages:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")

    api("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    api("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
}