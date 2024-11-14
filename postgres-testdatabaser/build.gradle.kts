val testcontainersVersion: String by project
val kotlinxCoroutinesVersion: String by project
val flywayVersion = "10.17.1"
val postgresqlVersion = "42.7.3"
val hikariCPVersion = "5.1.0"
val jacksonVersion: String by project

dependencies {
    api("org.testcontainers:postgresql:$testcontainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
    // konsumenter av biblioteket må selv vurdere hvilken flyway og hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion") {
        constraints {
            api("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
            api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
        }
    }
    implementation("org.postgresql:postgresql:$postgresqlVersion") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
}
