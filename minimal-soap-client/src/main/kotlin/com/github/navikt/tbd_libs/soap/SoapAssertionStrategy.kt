package com.github.navikt.tbd_libs.soap

import com.github.navikt.tbd_libs.soap.SoapAssertionStrategy.SoapAssertionResult
import org.intellij.lang.annotations.Language

fun samlStrategy(username: String, password: String) =
    SoapAssertionStrategy {
        when (val result = it.samlToken(username, password)) {
            is SamlTokenProvider.SamlTokenResult.Error -> SoapAssertionResult.Error(result.error, result.exception)
            is SamlTokenProvider.SamlTokenResult.Ok -> SoapAssertionResult.Ok(result.token.token)
        }
    }

fun usernamePasswordStrategy(username: String, password: String): SoapAssertionStrategy {
    @Language("XML")
    val template = """<wsse:UsernameToken>
    <wsse:Username>{{username}}</wsse:Username>
    <wsse:Password>{{password}}</wsse:Password>
</wsse:UsernameToken>"""
    return SoapAssertionStrategy {
        SoapAssertionResult.Ok(template
            .replace("{{username}}", username)
            .replace("{{password}}", password))
    }
}

fun interface SoapAssertionStrategy {
    fun token(provider: SamlTokenProvider): SoapAssertionResult

    sealed interface SoapAssertionResult {
        data class Ok(val body: String) : SoapAssertionResult
        data class Error(val error: String, val exception: Throwable? = null) : SoapAssertionResult
    }
}
