plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    testImplementation(platform(libs.jackson3.bom))
    testImplementation("tools.jackson.module:jackson-module-kotlin")
}
