val kafkaVersion = "3.9.0"

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation(project(":kafka-test"))
}