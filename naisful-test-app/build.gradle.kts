dependencies {
    api(project(":naisful-app"))
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.server.test.host) {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
}
