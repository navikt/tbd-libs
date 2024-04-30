val jacksonVersion: String by project
val mockkVersion: String by project

dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api(project(":signed-jwt"))

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}