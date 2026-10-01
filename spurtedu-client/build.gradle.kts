plugins {
    id("no.nav.sykepenger.kotlin")
}

dependencies {
    api(project(":azure-token-client"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
