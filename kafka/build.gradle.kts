val kafkaVersion = "3.8.0"
val kafkaTestcontainerVersion = "1.20.1"

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation("org.testcontainers:kafka:$kafkaTestcontainerVersion")
}