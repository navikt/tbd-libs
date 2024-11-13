val testcontainersVersion: String by project
val jacksonVersion: String by project
val kafkaVersion = "3.9.0"

dependencies {
    api("org.testcontainers:kafka:$testcontainersVersion")
    // konsumenter av biblioteket må selv vurdere hvilken kafkaversjon de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}
