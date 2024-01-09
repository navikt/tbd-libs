val jacksonVersion = "2.16.1"
val mockkVersion = "1.13.9"
val orgJsonVersion = "20231013"

dependencies {
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.json:json:$orgJsonVersion")
}