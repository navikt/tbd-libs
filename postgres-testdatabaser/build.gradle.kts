val testcontainersVersion = "1.19.3"
val flywayVersion = "10.5.0"
val postgresqlVersion = "42.7.1"
val hikariCPVersion = "5.1.0"

dependencies {
    api("org.testcontainers:postgresql:$testcontainersVersion")

    // konsumenter av biblioteket må selv vurdere hvilken flyway og hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
}
