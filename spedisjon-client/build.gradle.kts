dependencies {
    api(project(":azure-token-client"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}