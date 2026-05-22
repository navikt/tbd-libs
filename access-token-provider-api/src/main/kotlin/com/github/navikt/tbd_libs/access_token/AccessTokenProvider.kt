package com.github.navikt.tbd_libs.access_token

/**
 * Tilbyr access tokens for autentisering av utgående tjeneste-til-tjeneste-kall.
 *
 * Implementasjoner forventes å håndtere tokenhenting, mellomlagring og fornyelse.
 */
interface AccessTokenProvider {

    /**
     * Returnerer et maskin-til-maskin (client credentials) bearer-token for det angitte [scope].
     *
     * Bruk denne når du kaller en annen tjeneste på vegne av applikasjonen selv,
     * ikke på vegne av en sluttbruker.
     *
     * @param scope Scope for tjenesten som kalles
     *   (f.eks. `api://min-tjeneste/.default`).
     * @return En rå bearer token som kan sendes i en `Authorization: Bearer <token>`-header.
     * @throws AccessTokenException ved en uventet feil
     */
    fun machineToken(scope: String): String

    /**
     * Returnerer et On-Behalf-Of (OBO) bearer-token for det angitte [scope], vekslet inn fra [accessToken].
     *
     * Bruk denne når du kaller en annen tjeneste på vegne av en sluttbruker.
     * Det innkommende [accessToken] (typisk fra gjeldende HTTP-forespørsel)
     * veksles inn mot et nytt token scopet til [scope].
     *
     * @param accessToken Bearer tokenet som representerer den gjeldende sluttbrukeren,
     *   slik det ble mottatt i den innkommende forespørselen.
     * @param scope Scope for den nedstrøms tjenesten som kalles
     *   (f.eks. `api://min-tjeneste/.default`).
     * @return En rå bearer token som kan sendes i en `Authorization: Bearer <token>`-header.
     * @throws AccessTokenException ved en uventet feil
     */
    fun oboToken(accessToken: String, scope: String): String
}

class AccessTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

