plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    implementation(libs.wiremock)
    implementation(libs.java.jwt)

    testImplementation(platform(libs.jackson3.bom))
    testImplementation("tools.jackson.core:jackson-databind")
}
