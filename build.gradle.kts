val gradleversjon = "8.10"
val junitJupiterVersion = "5.11.0"
val mockkVersion = "1.13.13"
val jacksonVersion = "2.18.1"
val testcontainersVersion = "1.20.3"
val kotlinxCoroutinesVersion = "1.9.0"

plugins {
    kotlin("jvm") version "2.0.10" apply false
    `maven-publish`
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")

    ext.set("testcontainersVersion", testcontainersVersion)
    ext.set("jacksonVersion", jacksonVersion)
    ext.set("mockkVersion", mockkVersion)
    ext.set("kotlinxCoroutinesVersion", kotlinxCoroutinesVersion)

    val testImplementation by configurations
    val testRuntimeOnly by configurations
    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
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
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }
    }
}

tasks {
    withType<Wrapper> {
        gradleVersion = gradleversjon
    }
}
