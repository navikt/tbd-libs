dependencies {
    api(project(":azure-token-client"))
    api(project(":result-object"))

    testImplementation("io.mockk:mockk")
    testImplementation(project(":mock-http-client"))
}