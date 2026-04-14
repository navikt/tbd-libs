dependencies {
    api(platform(libs.jackson2.bom))
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation(kotlin("test"))
}
