package com.github.navikt.tbd_libs.populasjonstilgang.client

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

private data class MinimalTilgangsmaskinenResponse(
    val title: String
)

class TilgangsmaskinenClient(
    private val scope: String,
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
): PopulasjonstilgangskontrollProvider {
    private val objectMapper = jacksonMapperBuilder()
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    override fun kontrollerKomplettTilgang(accessToken: String, fødselsnummer: String): TilgangskontrollResultat {
        val oboToken = tokenProvider.oboToken(accessToken = accessToken, scope = scope)

        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/api/v1/komplett"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $oboToken")
            .header("callId", UUID.randomUUID().toString())
            .method("POST", HttpRequest.BodyPublishers.ofString(fødselsnummer))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return håndterResponse(response)
    }

    override fun kontrollerKjerneTilgang(accessToken: String, fødselsnummer: String): TilgangskontrollResultat {
        val oboToken = tokenProvider.oboToken(accessToken = accessToken, scope = scope)

        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/api/v1/kjerne"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $oboToken")
            .header("callId", UUID.randomUUID().toString())
            .method("POST", HttpRequest.BodyPublishers.ofString(fødselsnummer))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return håndterResponse(response)
    }

    override fun kontrollerKjerneTilgangForAnsatt(ansattId: String, fødselsnummer: String): TilgangskontrollResultat {
        val machineToken = tokenProvider.machineToken(scope = scope)

        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/api/v1/ccf/kjerne/$ansattId"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $machineToken")
            .header("callId", UUID.randomUUID().toString())
            .method("POST", HttpRequest.BodyPublishers.ofString(fødselsnummer))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return håndterResponse(response)
    }

    private fun håndterResponse(response: HttpResponse<String>): TilgangskontrollResultat {
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
            tokenProvider: AccessTokenProvider = TexasClient.fromEnv()
        ): TilgangsmaskinenClient {
            val prod = env["NAIS_CLUSTER_NAME"]?.startsWith("prod") ?: false
            val scope = if (prod) "api://prod-gcp.tilgangsmaskin.populasjonstilgangskontroll/.default" else "api://dev-gcp.tilgangsmaskin.populasjonstilgangskontroll/.default"
            val baseUrl = "http://populasjonstilgangskontroll.tilgangsmaskin"
            return TilgangsmaskinenClient(scope, baseUrl, tokenProvider)
        }
    }
}


