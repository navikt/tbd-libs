package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.soap.SamlTokenProvider.SamlTokenResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Base64

class MinimalStsClient(
    private val baseUrl: URI,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val proxyAuthorization: (() -> String)? = null,
) : SamlTokenProvider {
    override fun samlToken(username: String, password: String): SamlTokenResult {
        return try {
            val body = requestSamlToken(username, password) ?: return SamlTokenResult.Error("Tom responskropp fra STS")
            decodeSamlTokenResponse(body)
        } catch (err: Exception) {
            SamlTokenResult.Error("Feil ved henting av SAML-token: ${err.message}", err)
        }
    }

    private fun requestSamlToken(username: String, password: String): String? {
        val encodedCredentials = Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
        val proxyAuthorizationToken = proxyAuthorization?.invoke()
        val request = HttpRequest.newBuilder(URI("$baseUrl/rest/v1/sts/samltoken"))
            .header("Authorization", "Basic $encodedCredentials")
            .apply { if (proxyAuthorizationToken != null) this.header("X-Proxy-Authorization", proxyAuthorizationToken) }
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun decodeSamlTokenResponse(body: String): SamlTokenResult {
        val node = try {
            objectMapper.readTree(body)
        } catch (err: Exception) {
            return SamlTokenResult.Error("Kunne ikke tolke JSON fra responsen til STS: $body", err)
        }
        return extractSamlTokenFromResponse(node) ?: handleErrorResponse(node)
    }

    private fun extractSamlTokenFromResponse(node: JsonNode): SamlTokenResult? {
        val accessToken = node.path("access_token").takeIf(JsonNode::isTextual)?.asText() ?: return null
        val issuedTokenType = node.path("issued_token_type").takeIf(JsonNode::isTextual)?.asText() ?: return null
        val expiresIn = node.path("expires_in").takeIf(JsonNode::isNumber)?.asLong() ?: return null
        if (issuedTokenType != "urn:ietf:params:oauth:token-type:saml2") return SamlTokenResult.Error("Ukjent token type: $issuedTokenType")
        return try {
            SamlTokenResult.Ok(SamlToken(
                Base64.getDecoder().decode(accessToken).decodeToString(),
                LocalDateTime.now().plusSeconds(expiresIn)
            ))
        } catch (err: Exception) {
            SamlTokenResult.Error("Kunne ikke dekode Base64: ${err.message}", err)
        }
    }

    private fun handleErrorResponse(node: JsonNode): SamlTokenResult {
        val title = node.path("title").asText()
        val errorDetail = node.path("detail").takeIf(JsonNode::isTextual) ?: return SamlTokenResult.Error("Ukjent respons fra STS: $node")
        return SamlTokenResult.Error("Feil fra STS: $title - $errorDetail")
    }
}