package com.github.navikt.tbd_libs.azure

class AzureClientException(override val message: String, override val cause: Throwable? = null) : RuntimeException()