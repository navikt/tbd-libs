dependencies {
    api(platform(libs.jackson2.bom))
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api(project(":result-object"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
