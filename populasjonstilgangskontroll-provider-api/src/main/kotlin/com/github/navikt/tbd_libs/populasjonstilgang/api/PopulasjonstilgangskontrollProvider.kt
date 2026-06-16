package com.github.navikt.tbd_libs.populasjonstilgang.api

/**
 * Grensesnitt for å kontrollere en saksbehandlers tilgang til en person.
 */
interface PopulasjonstilgangskontrollProvider {
    /**
     * Kontrollerer om saksbehandleren som er autentisert via [accessToken] har tilgang til personen
     * identifisert av [fødselsnummer]. Evaluerer et komplett regelsett.
     *
     * @param accessToken JWT-accesstoken for den innloggede saksbehandleren.
     * @param fødselsnummer Fødselsnummeret til personen det skal sjekkes tilgang for.
     * @return [TilgangskontrollResultat.Ok] dersom tilgang er innvilget,
     *   [TilgangskontrollResultat.ManglerTilgang] dersom saksbehandleren mangler nødvendig tilgang,
     *   [TilgangskontrollResultat.IdentIkkeFunnet] dersom personen ikke ble funnet,
     *   eller [TilgangskontrollResultat.UventetFeil] ved en uventet feil.
     */
    fun kontrollerKomplettTilgang(accessToken: String, fødselsnummer: String): TilgangskontrollResultat

    /**
     * Kontrollerer om saksbehandleren som er autentisert via [accessToken] har tilgang til personen
     * identifisert av [fødselsnummer]. Evaluerer mot kjerneregelsett.
     *
     * @param accessToken JWT-accesstoken for den innloggede saksbehandleren.
     * @param fødselsnummer Fødselsnummeret til personen det skal sjekkes tilgang for.
     * @return [TilgangskontrollResultat.Ok] dersom tilgang er innvilget,
     *   [TilgangskontrollResultat.ManglerTilgang] dersom saksbehandleren mangler nødvendig tilgang,
     *   [TilgangskontrollResultat.IdentIkkeFunnet] dersom personen ikke ble funnet,
     *   eller [TilgangskontrollResultat.UventetFeil] ved en uventet feil.
     */
    fun kontrollerKjerneTilgang(accessToken: String, fødselsnummer: String): TilgangskontrollResultat
}
