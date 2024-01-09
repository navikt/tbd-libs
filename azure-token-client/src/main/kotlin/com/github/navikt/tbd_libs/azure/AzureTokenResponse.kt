package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Duration
import java.time.LocalDateTime

internal data class AzureTokenResponse(
    @JsonProperty("token_type")
    val tokenType: String,
    @JsonProperty("access_token")
    val token: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JacksonInject
    private val utstedtTidspunkt: LocalDateTime
) {
    val expirationTime: LocalDateTime = utstedtTidspunkt.plusSeconds(expiresIn)

    init {
        check(tokenType.lowercase() == "bearer") {
            "Forventer kun token av typen Bearer. Fikk $tokenType"
        }
    }
}