val mockkVersion: String by project

dependencies {
    api(project(":azure-token-client"))
    api(project(":result-object"))

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}