package com.github.navikt.tbd_libs.azure

import com.github.navikt.tbd_libs.azure.JsonSerde.Companion.deserializeOrNull
import com.github.navikt.tbd_libs.azure.JsonSerde.Companion.stringOrNull

internal data class AzureErrorResponse(
    val error: String,
    val description: String
) {
    internal companion object {
        internal fun JsonSerde.azureErrorResponseOrNull(body: String): AzureErrorResponse? {
            val json = deserializeOrNull(body) ?: return null
            val error = json.stringOrNull("error") ?: return null
            val errorDescription = json.stringOrNull("error_description") ?: return null
            return AzureErrorResponse(error, errorDescription)
        }
    }
}