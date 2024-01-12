val jacksonVersion: String by project
val mockkVersion: String by project

dependencies {
    api(project(":minimal-sts-client"))

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}