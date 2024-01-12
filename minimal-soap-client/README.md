Minimal SOAP Client
===================

Tar i bruk `minimal-sts-client` for å hente SAML token, som så sendes inn i requesten.

**DU** er selv ansvarlig for å sende `<soap:Body>`-delen av requesten _og_ parse responsen fra tjenesten.

Med denne klienten kan du kvitte deg med CXF.


```kotlin
val username = "srvfoo..."
val password = "secret"

val samlTokenClient = MinimalStsClient(URI("https://security-token-service.dev.adeo.no"), httpClient)
val soapClient = MinimalSoapClient(URI("https://arena-q2-adeo.no/ail_ws/MeldekortUtbetalingsgrunnlag_v1"), samlTokenClient, httpClient)

val assertionStrategoy = samlStrategy(username, password)
@Language("XML")
val requestBody = """<ns2:finnMeldekortUtbetalingsgrunnlagListe xmlns:ns2="http://nav.no/tjeneste/virksomhet/meldekortUtbetalingsgrunnlag/v1">
    <request>
        <ident xmlns:ns4="http://nav.no/tjeneste/virksomhet/meldekortUtbetalingsgrunnlag/v1/informasjon" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:Bruker">
            <ident>02889298149</ident>
        </ident>
        <periode>
            <fom>1970-01-01+01:00</fom>
            <tom>1970-01-01+01:00</tom>
        </periode>
        <temaListe>AAP</temaListe>
    </request>
</ns2:finnMeldekortUtbetalingsgrunnlagListe>"""

val result = soapClient.doSoapAction(
    action = "http://nav.no/tjeneste/virksomhet/meldekortUtbetalingsgrunnlag/v1/meldekortUtbetalingsgrunnlag_v1/finnMeldekortUtbetalingsgrunnlagListeRequest",
    body = requestBody,
    tokenStrategy = assertionStrategoy
)
```