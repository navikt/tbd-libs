dependencies {
    api(project(":populasjonstilgangskontroll-provider-api"))
    implementation(project(":access-token-provider-texas"))
    api(project(":access-token-provider-api"))
    implementation(project(":jackson"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
