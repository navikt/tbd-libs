dependencies {
    api(project(":azure-token-client"))

    testImplementation("io.mockk:mockk")
    testImplementation(project(":mock-http-client"))
}