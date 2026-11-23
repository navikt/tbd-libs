plugins {
    id("no.nav.sykepenger.kotlin")
}

dependencies {
    api(project(":populasjonstilgangskontroll-provider-api"))
    implementation(project(":access-token-provider-texas"))
    api(project(":access-token-provider-api"))
    implementation(project(":jackson"))
    implementation(project(":retry"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
