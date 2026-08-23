import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    `maven-publish`
}

allprojects {
    repositories {
        mavenCentral()
        // for "com.github.navikt:rapids-and-rivers"-biblioteket som speil-backend-app bruker.
        // repo.adeo.no er Navs interne Nexus-speil (kun tilgjengelig på Navs nett), mens GitHub Actions-runnere
        // må gå via GitHub sin egen package registry-proxy for navikt-organisasjonen.
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
            maven("https://maven.pkg.github.com/navikt/maven-release") {
                credentials {
                    username = "token"
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull
                }
            }
        } else {
            maven("https://repo.adeo.no/repository/github-package-registry-navikt/")
        }
    }
}

// libs-accessoren er ikke tilgjengelig inne i subprojects-blokka, så den slås opp her
val jackson3Bom = libs.jackson3.bom

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")


    val api by configurations
    val testImplementation by configurations
    val testRuntimeOnly by configurations
    dependencies {
        constraints {
            api(jackson3Bom) {
                because("Jackson 3 < 3.1.0 har sikkerhetshull")
            }
        }

        testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of("21"))
        }
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = "com.github.navikt.tbd-libs"
                artifactId = project.name
                version = "${this@subprojects.version}"
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

    tasks {
        withType<Jar> {
            manifest {
                attributes(mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version
                ))
            }
        }

        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }

            systemProperty("junit.jupiter.execution.parallel.enabled", "true")
            systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
            systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
            systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
        }
    }
}
