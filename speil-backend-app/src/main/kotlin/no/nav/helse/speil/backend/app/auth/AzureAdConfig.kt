package no.nav.helse.speil.backend.app.auth

data class AzureAdConfig(
    val clientId: String,
    val issuerUrl: String,
    val jwkProviderUri: String,
) {
    companion object {
        fun fraEnv(env: Map<String, String> = System.getenv()) =
            AzureAdConfig(
                clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                issuerUrl = env.getValue("AZURE_APP_ISSUER_URL"),
                jwkProviderUri = env.getValue("AZURE_APP_JWK_PROVIDER_URI"),
            )
    }
}
