plugins {
    id("no.nav.sykepenger.kotlin")
}

dependencies {
    implementation(libs.valkey.java)
    testImplementation(libs.testcontainers.core)
}
