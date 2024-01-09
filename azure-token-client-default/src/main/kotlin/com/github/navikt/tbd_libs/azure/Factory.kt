package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient

fun createDefaultAzureTokenClient(
    tokenEndpoint: URI,
    clientId: String,
    clientSecret: String,
    httpClient: HttpClient = HttpClient.newHttpClient(),
    objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
): AzureTokenProvider {
    return InMemoryAzureTokenCache(AzureTokenClient(
        tokenEndpoint = tokenEndpoint,
        clientId = clientId,
        authMethod = AzureAuthMethod.Secret(clientSecret),
        client = httpClient,
        objectMapper = objectMapper
    ))
}

fun createAzureTokenClientFromEnvironment(env: Map<String, String> = System.getenv()) =
    createDefaultAzureTokenClient(
        tokenEndpoint = URI(env.getValue("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")),
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
        clientSecret = env.getValue("AZURE_APP_CLIENT_SECRET")
    )