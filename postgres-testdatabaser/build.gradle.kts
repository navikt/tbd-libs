val testcontainersVersion = "1.20.1"
val flywayVersion = "10.17.1"
val postgresqlVersion = "42.7.3"
val hikariCPVersion = "5.1.0"

dependencies {
    api("org.testcontainers:postgresql:$testcontainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0-RC.2")
    // konsumenter av biblioteket må selv vurdere hvilken flyway og hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
}
