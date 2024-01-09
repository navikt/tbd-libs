package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.azure.JsonSerde.Companion.deserializeOrNull
import com.github.navikt.tbd_libs.azure.JsonSerde.Companion.longOrNull
import com.github.navikt.tbd_libs.azure.JsonSerde.Companion.stringOrNull
import java.time.LocalDateTime

internal data class AzureTokenResponse(
    val tokenType: String,
    val token: String,
    val expiresIn: Long,
    private val utstedtTidspunkt: LocalDateTime
) {
    val expirationTime: LocalDateTime = utstedtTidspunkt.plusSeconds(expiresIn)

    init {
        check(tokenType.lowercase() == "bearer") {
            "Forventer kun token av typen Bearer. Fikk $tokenType"
        }
    }

    internal companion object {
        internal fun JsonSerde.azureTokenResponseOrNull(body: String, utstedtTidspunkt: LocalDateTime): AzureTokenResponse? {
            val json = deserializeOrNull(body) ?: return null
            val tokenType = json.stringOrNull("token_type") ?: return null
            val accessToken = json.stringOrNull("access_token") ?: return null
            val expiresIn = json.longOrNull("expires_in") ?: return null
            return AzureTokenResponse(tokenType, accessToken, expiresIn, utstedtTidspunkt)
        }
    }
}