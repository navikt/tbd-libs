val jacksonVersion: String by project

dependencies {
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}