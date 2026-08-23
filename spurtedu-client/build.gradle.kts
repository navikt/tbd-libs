plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(project(":azure-token-client"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
