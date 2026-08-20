package no.nav.helse.speil.backend.app.bootstrap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppKonfigurasjonTest {
    private val fullEnv =
        mapOf(
            "AZURE_APP_CLIENT_ID" to "client-id",
            "AZURE_APP_ISSUER_URL" to "https://issuer.example",
            "AZURE_APP_JWK_PROVIDER_URI" to "https://issuer.example/jwks",
            "DATABASE_JDBC_URL" to "jdbc:postgresql://localhost:5432/mydb",
            "TILGANGSMASKINEN_SCOPE" to "api://cluster.tilgangsmaskin.populasjonstilgangskontroll/.default",
            "TILGANGSMASKINEN_BASE_URL" to "http://populasjonstilgangskontroll.tilgangsmaskin",
            "TILGANG_LES" to "les-uuid",
            "TILGANG_SKRIV" to "skriv-uuid",
        )

    @Test
    fun `fraEnv bygger komplett konfigurasjon fra fullstendig miljo`() {
        val konfigurasjon = AppKonfigurasjon.fraEnv("sp-vilkarsproving", fullEnv)
        assertEquals("sp-vilkarsproving", konfigurasjon.appNavn)
        assertEquals("client-id", konfigurasjon.azureAd.clientId)
        assertEquals("jdbc:postgresql://localhost:5432/mydb", konfigurasjon.database.jdbcUrl)
        assertEquals(false, konfigurasjon.openApi.eksponerOpenApi)
    }
}
