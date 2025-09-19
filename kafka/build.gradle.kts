val kafkaVersion = "4.1.0"

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation(project(":kafka-test"))
}
