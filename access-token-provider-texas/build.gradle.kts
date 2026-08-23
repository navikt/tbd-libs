plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    implementation(project(":access-token-provider-api"))
    implementation(project(":jackson"))
    implementation(project(":retry"))
    testImplementation(project(":mock-http-client"))
    testImplementation(libs.mockk)
}
