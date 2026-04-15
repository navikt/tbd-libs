dependencies {
    api(libs.testcontainers.postgresql)
    implementation(libs.kotlinx.coroutines)
    // konsumenter av biblioteket må selv vurdere hvilken flyway og hikari de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql) {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.hikariCP)
}
