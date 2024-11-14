val slf4jVersion = "2.0.16"
val ktorVersion = "3.0.1"
val micrometerRegistryPrometheusVersion = "1.13.6"
val jacksonVersion: String by project

dependencies {
    api("org.slf4j:slf4j-api:$slf4jVersion")

    api("io.ktor:ktor-server-cio:$ktorVersion")
    api("io.ktor:ktor-server-call-id:$ktorVersion")
    api("io.ktor:ktor-server-status-pages:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-jackson:$ktorVersion")

    api("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    api("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion") {
        constraints {
            api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
            api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
        }
    }

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}