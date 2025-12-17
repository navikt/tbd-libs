val ktorVersion = "3.3.3"

dependencies {
    api(project(":naisful-app"))
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
}
