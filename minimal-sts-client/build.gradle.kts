plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(platform(libs.jackson3.bom))
    api("tools.jackson.module:jackson-module-kotlin")
    api(project(":result-object"))

    testImplementation(libs.mockk)
    testImplementation(project(":mock-http-client"))
}
