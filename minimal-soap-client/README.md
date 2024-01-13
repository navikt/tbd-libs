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

## Deserialisere responskropp til en dataklasse

Ta utgangspunkt i en tjeneste som svarer med
```xml
<?xml version='1.0' encoding='UTF-8'?>
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/"
            xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    <S:Header>
        ...
    </S:Header>
    <S:Body>
        <ns2:finnMeldekortUtbetalingsgrunnlagListeResponse xmlns:ns2="http://example.org/foo/bar">
            <response>
                <meldekortUtbetalingsgrunnlagListe>
                    <vedtakListe>
                        <meldekortListe>
                            <meldekortperiode>
                                <fom>2018-01-01</fom>
                                <tom>2018-01-31</tom>
                            </meldekortperiode>
                            <dagsats>500.0</dagsats>
                            <beloep>500.0</beloep>
                            <utbetalingsgrad>100.0</utbetalingsgrad>
                        </meldekortListe>
                        <vedtaksperiode>
                            <fom>2018-01-01</fom>
                            <tom>2018-01-31</tom>
                        </vedtaksperiode>
                        <vedtaksstatus termnavn="Avsluttet">AVSLU</vedtaksstatus>
                        <vedtaksdato>2018-01-01</vedtaksdato>
                        <datoKravMottatt>2018-01-01</datoKravMottatt>
                        <dagsats>500</dagsats>
                    </vedtakListe>
                    <fagsystemSakId>asdasdasdasdasdasdasda</fagsystemSakId>
                    <saksstatus termnavn="Inaktiv">INAKT</saksstatus>
                    <tema termnavn="AAP">AAP</tema>
                </meldekortUtbetalingsgrunnlagListe>
            </response>
        </ns2:finnMeldekortUtbetalingsgrunnlagListeResponse>
    </S:Body>
</S:Envelope>
```

Da kan vi lage følgende dataklasser for `<soap:Body>`-elementet:

```kotlin
data class SoapBody(
    @JacksonXmlProperty(
        localName = "finnMeldekortUtbetalingsgrunnlagListeResponse",
        namespace = "http://example.org/foo/bar"
    )
    val finnMeldekortResponse: FinnMeldekortResponse
) {
    companion object {
        // en objectmapper som funker til å deserialisere dataklassen;
        // dvs. den er konfigurert til å matche kravene til dataklassen.
        // Fordi det varierer mellom soap-tjeneste til soap-tjeneste så
        // er det nok overkill å skulle tilby én felles-funker-for-alle-objectMapper
        fun bodyHandler(): ObjectMapper {
            return XmlMapper.builder()
                .addModules(JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // issue: https://github.com/FasterXML/jackson-module-kotlin/issues/138
                // workaround: https://github.com/FasterXML/jackson-module-kotlin/issues/138#issuecomment-576484905
                .nameForTextElement("innerText")
                .build()
        }
    }
}

data class FinnMeldekortResponse(
    @JacksonXmlProperty(localName = "response")
    private val response: Response
)

data class Response(
    @JacksonXmlProperty(localName = "meldekortUtbetalingsgrunnlagListe")
    @JacksonXmlElementWrapper(useWrapping = false)
    val meldekortUtbetalingsgrunnlagListe: List<Sak>
)

data class Sak(
    @JacksonXmlProperty(localName = "fagsystemSakId")
    val fagsystemSakId: String,

    @JacksonXmlProperty(localName = "saksstatus")
    val saksstatus: Saksstatus,

    @JacksonXmlProperty(localName = "tema")
    val tema: Tema,

    @JacksonXmlProperty(localName = "vedtakListe")
    @JacksonXmlElementWrapper(useWrapping = false)
    val vedtaksliste: List<Vedtak>
)

data class Saksstatus(
    @JacksonXmlProperty(isAttribute = true, localName = "termnavn")
    val termnavn: String,
    @JacksonXmlProperty(localName = "innerText")
    val verdi: String
)

data class Tema(
    @JacksonXmlProperty(isAttribute = true, localName = "termnavn")
    var termnavn: String,
    // issue: https://github.com/FasterXML/jackson-module-kotlin/issues/138
    // workaround: https://github.com/FasterXML/jackson-module-kotlin/issues/138#issuecomment-576484905
    @JacksonXmlProperty(localName = "innerText")
    val verdi: String
)

data class Vedtak(
    @JacksonXmlProperty(localName = "meldekortListe")
    @JacksonXmlElementWrapper(useWrapping = false)
    val meldekortliste: List<Meldekort>,
    @JacksonXmlProperty(localName = "vedtaksperiode")
    val vedtaksperiode: Periode,
    @JacksonXmlProperty(localName = "vedtaksstatus")
    val vedtaksstatus: Vedtaksstatus,
    @JacksonXmlProperty(localName = "vedtaksdato")
    val vedtaksdato: LocalDate,
    @JacksonXmlProperty(localName = "datoKravMottatt")
    val datoKravMottatt: LocalDate,
    @JacksonXmlProperty(localName = "dagsats")
    val dagsats: Double
)

data class Vedtaksstatus(
    @JacksonXmlProperty(isAttribute = true, localName = "termnavn")
    val termnavn: String,
    @JacksonXmlProperty(localName = "innerText")
    val verdi: String
)

data class Meldekort(
    @JacksonXmlProperty(localName = "meldekortperiode")
    val meldekortperiode: Periode,
    @JacksonXmlProperty(localName = "dagsats")
    val dagsats: Double,
    @JacksonXmlProperty(localName = "beloep")
    val beløp: Double,
    @JacksonXmlProperty(localName = "utbetalingsgrad")
    val utbetalingsgrad: Double
)

data class Periode(
    @JacksonXmlProperty(localName = "fom")
    val fom: LocalDate,
    @JacksonXmlProperty(localName = "tom")
    val tom: LocalDate
)
```

Da er det "enkelt" å deserialisere XML'en:

```kotlin
val responseHandler = SoapResponseHandler(SoapBody.bodyHandler())
try {
    val result = responseHandler.deserializeSoapBody(response, SoapBody::class)
    // do something...
} catch (err: SoapResponseHandlerException) {
    println(err.message)
}
```