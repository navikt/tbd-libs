plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    implementation(libs.valkey.java)
    testImplementation(libs.testcontainers.core)
}
