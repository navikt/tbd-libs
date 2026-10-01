plugins {
    id("no.nav.sykepenger.kotlin")
}

dependencies {
    api(libs.kafka.clients)

    testImplementation(project(":kafka-test"))
}
