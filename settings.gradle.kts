rootProject.name = "tbd-libs"
include(
    "azure-token-client",
    "azure-token-client-default",
    "kafka",
    "kafka-test",
    "mock-http-client",
    "minimal-sts-client",
    "minimal-soap-client",
    "naisful-app",
    "naisful-test-app",
    "naisful-postgres",
    "postgres-testdatabaser",
    "retry",
    "rapids-and-rivers-api",
    "rapids-and-rivers-test",
    "rapids-and-rivers",
    "result-object",
    "signed-jwt",
    "signed-jwt-issuer-test",
    "spurtedu-client",
    "speed-client",
    "spenn-simulering-client",
    "spedisjon-client",
    "jackson",
    "sql-dsl",
    "person-pseudo-id",
    "access-token-provider-api",
    "access-token-provider-texas",
    "populasjonstilgangskontroll-provider-api",
    "populasjonstilgangskontroll-provider-tilgangsmaskinen",
    "speil-backend-app",
)

// Sett opp repositories basert på om vi kjører i CI eller ikke
// Jf. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
pluginManagement {
    repositories {
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
            maven("https://maven.pkg.github.com/navikt/maven-release") {
                credentials {
                    username = "token"
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull!!
                }
            }
        } else {
            maven("https://repo.adeo.no/repository/github-package-registry-navikt/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    // Bare tillat repositories-oppsett her i settings.gradle.kts
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
            maven("https://maven.pkg.github.com/navikt/maven-release") {
                credentials {
                    username = "token"
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull!!
                }
            }
        } else {
            maven("https://repo.adeo.no/repository/github-package-registry-navikt/")
        }
        mavenCentral()
    }
}
