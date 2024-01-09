package com.github.navikt.tbd_libs.azure

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AzureErrorResponse(
    @JsonProperty("error")
    val error: String,
    @JsonProperty("error_description")
    val description: String
)