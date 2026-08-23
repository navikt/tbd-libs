plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(project(":rapids-and-rivers-api"))

    implementation(platform(libs.jackson3.bom))
    implementation("tools.jackson.module:jackson-module-kotlin")
}
