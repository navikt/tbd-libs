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

    /**
     * Kontrollerer om NAV-ansatt identifisert av [ansattId] har tilgang til personen identifisert av
     * [fødselsnummer]. Evaluerer mot kjerneregelsett. Kalles med maskin-til-maskin-token (client
     * credentials flow), siden det ikke finnes en innlogget saksbehandler med et accessToken i denne
     * flyten.
     *
     * @param ansattId NAV-identen (ansatt-ID) til saksbehandleren det skal sjekkes tilgang for.
     * @param fødselsnummer Fødselsnummeret til personen det skal sjekkes tilgang for.
     * @return [TilgangskontrollResultat.Ok] dersom tilgang er innvilget,
     *   [TilgangskontrollResultat.ManglerTilgang] dersom saksbehandleren mangler nødvendig tilgang,
     *   [TilgangskontrollResultat.IdentIkkeFunnet] dersom personen ikke ble funnet,
     *   eller [TilgangskontrollResultat.UventetFeil] ved en uventet feil.
     */
    fun kontrollerKjerneTilgangForAnsatt(ansattId: String, fødselsnummer: String): TilgangskontrollResultat
}
