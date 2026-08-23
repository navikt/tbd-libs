dependencies {
    api(project(":minimal-sts-client"))
    api(project(":result-object"))
    api(platform(libs.jackson3.bom))
    api("tools.jackson.dataformat:jackson-dataformat-xml")

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
