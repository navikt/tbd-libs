val postgresqlVersion = "42.7.5"
val hikariCPVersion = "6.3.0"

dependencies {
    // konsumenter av biblioteket må selv vurdere hvilken hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    testImplementation("com.zaxxer:HikariCP:$hikariCPVersion")
    testImplementation("org.postgresql:postgresql:$postgresqlVersion") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(project(":postgres-testdatabaser"))
}
