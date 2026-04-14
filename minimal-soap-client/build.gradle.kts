dependencies {
    api(project(":minimal-sts-client"))
    api(project(":result-object"))
    api(platform(libs.jackson2.bom))
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    testImplementation("io.mockk:mockk")
    testImplementation(project(":mock-http-client"))
}
