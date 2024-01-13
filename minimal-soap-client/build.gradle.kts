val jacksonVersion: String by project
val mockkVersion: String by project

dependencies {
    api(project(":minimal-sts-client"))
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}