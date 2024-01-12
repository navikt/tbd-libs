val jacksonVersion = "2.16.1"
val mockkVersion = "1.13.9"

dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(project(":mock-http-client"))
}