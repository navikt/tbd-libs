package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AzureTokenResponse(
    @param:JsonProperty("token_type")
    val tokenType: String,
    @param:JsonProperty("access_token")
    val token: String,
    @param:JsonProperty("expires_in")
    val expiresIn: Long,
    @param:JacksonInject
    private val utstedtTidspunkt: LocalDateTime
) {
    val expirationTime: LocalDateTime = utstedtTidspunkt.plusSeconds(expiresIn)

    init {
        check(tokenType.lowercase() == "bearer") {
            "Forventer kun token av typen Bearer. Fikk $tokenType"
        }
    }
}
