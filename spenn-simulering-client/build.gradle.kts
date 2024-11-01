val mockkVersion: String by project

dependencies {
    api(project(":azure-token-client"))

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}