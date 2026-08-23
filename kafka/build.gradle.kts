plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(libs.kafka.clients)

    testImplementation(project(":kafka-test"))
}
