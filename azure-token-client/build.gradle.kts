val slf4jApiVersion = "2.0.7"
val jacksonVersion = "2.16.1"
dependencies {
    implementation("org.slf4j:slf4j-api:$slf4jApiVersion")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
}