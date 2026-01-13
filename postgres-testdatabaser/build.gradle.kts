val testcontainersVersion: String by project
val flywayVersion = "11.5.0"
val postgresqlVersion = "42.7.7"
val hikariCPVersion = "6.3.0"

dependencies {
    api("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    // konsumenter av biblioteket må selv vurdere hvilken flyway og hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
}
