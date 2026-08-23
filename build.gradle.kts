plugins {
    alias(libs.plugins.sas.root)
    alias(libs.plugins.sas.kotlin) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

// libs-accessoren er ikke tilgjengelig inne i subprojects-blokka, så den slås opp her
val jackson3Bom = libs.jackson3.bom

subprojects {
    // Alle modulene i dette prosjektet er biblioteker som publiseres til GitHub Package Registry.
    plugins.withId("no.nav.helse.sas.sas-kotlin") {
        apply(plugin = "maven-publish")

        dependencies {
            constraints {
                add("api", jackson3Bom) {
                    because("Jackson 3 < 3.1.0 har sikkerhetshull")
                }
            }
        }

        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = "com.github.navikt.tbd-libs"
                    artifactId = project.name
                    version = "${project.version}"
                }
            }
            repositories {
                maven {
                    url = uri("https://maven.pkg.github.com/navikt/tbd-libs")
                    credentials {
                        username = System.getenv("GITHUB_USERNAME")
                        password = System.getenv("GITHUB_PASSWORD")
                    }
                }
            }
        }

        tasks.withType<Jar>().configureEach {
            manifest {
                attributes(
                    mapOf(
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version,
                    ),
                )
            }
        }

        // Kjør testene i hver modul parallelt. Moduler som ikke tåler det slår det av selv.
        tasks.withType<Test>().configureEach {
            systemProperty("junit.jupiter.execution.parallel.enabled", "true")
            systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
            systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
            systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
        }
    }
}
