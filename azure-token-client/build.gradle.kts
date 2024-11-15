dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api(project(":signed-jwt"))
    api(project(":result-object"))

    testImplementation("io.mockk:mockk")
    testImplementation(project(":mock-http-client"))
}