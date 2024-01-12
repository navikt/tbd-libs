package com.github.navikt.tbd_libs.soap

import org.intellij.lang.annotations.Language

fun samlStrategy(username: String, password: String) =
    SoapAssertionStrategy { it.samlToken(username, password).token }

fun usernamePasswordStrategy(username: String, password: String): SoapAssertionStrategy {
    @Language("XML")
    val template = """<wsse:UsernameToken>
    <wsse:Username>{{username}}</wsse:Username>
    <wsse:Password>{{password}}</wsse:Password>
</wsse:UsernameToken>"""
    return SoapAssertionStrategy {
        template
            .replace("{{username}}", username)
            .replace("{{password}}", password)
    }
}

fun interface SoapAssertionStrategy {
    fun token(provider: SamlTokenProvider): String
}
