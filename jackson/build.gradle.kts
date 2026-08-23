dependencies {
    api(platform(libs.jackson3.bom))
    api("tools.jackson.module:jackson-module-kotlin")
    testImplementation(kotlin("test"))
}
