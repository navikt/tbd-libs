val slf4jVersion = "2.0.16"
val micrometerRegistryPrometheusVersion = "1.13.3"

dependencies {
    api("org.slf4j:slf4j-api:$slf4jVersion")

    api("io.micrometer:micrometer-core:$micrometerRegistryPrometheusVersion")
}