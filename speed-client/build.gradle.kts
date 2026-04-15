dependencies {
    api(project(":azure-token-client"))
    api(project(":result-object"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}