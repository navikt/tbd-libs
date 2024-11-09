val kafkaVersion = "3.8.0"

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation(project(":kafka-test"))
}