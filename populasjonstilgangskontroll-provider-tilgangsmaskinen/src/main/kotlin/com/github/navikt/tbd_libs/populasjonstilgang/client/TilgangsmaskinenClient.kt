package com.github.navikt.tbd_libs.populasjonstilgang.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

private data class MinimalTilgangsmaskinenResponse(
    val title: String
)

class TilgangsmaskinenClient(
    private val scope: String,
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
): PopulasjonstilgangskontrollProvider {
    private val objectMapper = jacksonObjectMapper()
    override fun kontrollerTilgang(accessToken: String, fødselsnummer: String): TilgangskontrollResultat {
        val oboToken = tokenProvider.oboToken(accessToken = accessToken, scope = scope)

        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/komplett"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $oboToken")
            .header("callId", UUID.randomUUID().toString())
            .method("POST", HttpRequest.BodyPublishers.ofString(fødselsnummer))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        val statusCode = response.statusCode()
        if (statusCode == 204) {
            return TilgangskontrollResultat.Ok
        }
        if (statusCode == 403) {
            val tilgangsmaskinenResponse = objectMapper.readValue<MinimalTilgangsmaskinenResponse>(response.body())
            return when (tilgangsmaskinenResponse.title) {
                "AVVIST_STRENGT_FORTROLIG_ADRESSE" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresse)
                "AVVIST_STRENGT_FORTROLIG_UTLAND" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.StrengtFortroligAdresseUtland)
                "AVVIST_FORTROLIG_ADRESSE" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.FortroligAdresse)
                "AVVIST_SKJERMING" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.EgenAnsatt)
                "AVVIST_HABILITET" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)
                "AVVIST_VERGE" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Verge)
                "AVVIST_GEOGRAFISK" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.GeografiskTilhørighet)
                "AVVIST_AVDØD" -> TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.PersonDød)
                else -> TilgangskontrollResultat.UventetFeil("Uventet feilkode fra tilgangsmaskinen: $tilgangsmaskinenResponse")
            }
        }
        if (statusCode == 404) {
            return TilgangskontrollResultat.IdentIkkeFunnet
        }
        return TilgangskontrollResultat.UventetFeil("Uventet status fra tilgangsmaskinen: $statusCode")
    }

    companion object {
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
        ): TilgangsmaskinenClient {
            val tokenProvider = TexasClient.fromEnv()
            val prod = env["NAIS_CLUSTER_NAME"]?.startsWith("prod") ?: false
            val scope = if (prod) "api://prod-gcp.tilgangsmaskin.populasjonskontroll/.default" else "api://dev-gcp.tilgangsmaskin.populasjonskontroll/.default"
            val baseUrl = (if (prod) "https://tilgangsmaskin.intern.nav.no" else "https://tilgangsmaskin.intern.dev.nav.no") + "/api/v1"
            return TilgangsmaskinenClient(scope, baseUrl, tokenProvider)
        }
    }
}


