val jacksonVersion: String by project
val mockkVersion: String by project

dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}