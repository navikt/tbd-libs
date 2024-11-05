val ktorVersion = "3.0.1"

dependencies {
    api(project(":naisful-app"))
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-server-test-host:$ktorVersion")
}