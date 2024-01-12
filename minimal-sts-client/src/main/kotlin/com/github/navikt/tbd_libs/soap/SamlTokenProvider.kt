package com.github.navikt.tbd_libs.soap

interface SamlTokenProvider {
    fun samlToken(username: String, password: String): SamlToken
}