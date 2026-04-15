dependencies {
    // konsumenter av biblioteket må selv vurdere hvilken hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    testImplementation(libs.hikariCP)
    testImplementation(libs.postgresql) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(project(":postgres-testdatabaser"))
}
