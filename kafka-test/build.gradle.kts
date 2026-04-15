dependencies {
    api(libs.testcontainers.kafka)
    // konsumenter av biblioteket må selv vurdere hvilken kafkaversjon de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation(libs.kafka.clients) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(platform(libs.jackson2.bom))
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
