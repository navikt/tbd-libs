val ktorVersion = "3.0.1"

dependencies {
    implementation(project(":naisful-app"))

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
}