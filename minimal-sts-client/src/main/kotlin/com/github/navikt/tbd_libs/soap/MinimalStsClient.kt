package com.github.navikt.tbd_libs.soap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : SamlTokenProvider {
    override fun samlToken(username: String, password: String): SamlToken {
        val body = requestSamlToken(username, password)
        return decodeSamlTokenResponse(body)
    }

    private fun requestSamlToken(username: String, password: String): String {
        val encodedCredentials = Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
        val request = HttpRequest.newBuilder(URI("$baseUrl/rest/v1/sts/samltoken"))
            .header("Authorization", "Basic $encodedCredentials")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body() ?: throw StsClientException("Tom responskropp fra STS")
    }

    private fun decodeSamlTokenResponse(body: String): SamlToken {
        val node = try {
            objectMapper.readTree(body)
        } catch (err: Exception) {
            throw StsClientException("Kunne ikke tolke JSON fra responsen til STS: $body")
        }
        return extractSamlTokenFromResponse(node) ?: handleErrorResponse(node)
    }

    private fun extractSamlTokenFromResponse(node: JsonNode): SamlToken? {
        val accessToken = node.path("access_token").takeIf(JsonNode::isTextual)?.asText() ?: return null
        val issuedTokenType = node.path("issued_token_type").takeIf(JsonNode::isTextual)?.asText() ?: return null
        val expiresIn = node.path("expires_in").takeIf(JsonNode::isNumber)?.asLong() ?: return null
        if (issuedTokenType != "urn:ietf:params:oauth:token-type:saml2") throw StsClientException("Ukjent token type: $issuedTokenType")
        try {
            return SamlToken(
                Base64.getDecoder().decode(accessToken).decodeToString(),
                LocalDateTime.now().plusSeconds(expiresIn)
            )
        } catch (err: Exception) {
            throw StsClientException("Kunne ikke dekode Base64: ${err.message}", err)
        }
    }

    private fun handleErrorResponse(node: JsonNode): Nothing {
        val title = node.path("title").asText()
        val errorDetail = node.path("detail").takeIf(JsonNode::isTextual) ?: throw StsClientException("Ukjent respons fra STS: $node")
        throw StsClientException("Feil fra STS: $title - $errorDetail")
    }
}

class StsClientException(override val message: String, override val cause: Throwable? = null) : RuntimeException()