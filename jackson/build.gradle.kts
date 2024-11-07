val jacksonVersion: String by project

dependencies {
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testImplementation(kotlin("test"))
}
