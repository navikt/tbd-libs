dependencies {
    api(project(":rapids-and-rivers-api"))

    implementation(platform("com.fasterxml.jackson:jackson-bom"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
