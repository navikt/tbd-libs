# Tilgangsmaskinen Client

Dette er et wrapper-bibliotek på Tilgangsmaskinen. Tilgangsmaskinen utfører såkalt
populasjonstilgangskontroll. Det vil si at den sjekker om bruker X har tilgang til person Y.

Denne formen for tilgangskontroll er generell for Nav, og sjekker blant annet adressebeskyttelse og skjerming.
Les mer om alle sjekkene Tilgangsmaskinen gjør [her](https://confluence.adeo.no/spaces/TM/pages/628888614/Intro+til+Tilgangsmaskinen#IntrotilTilgangsmaskinen-Hvilkereglersjekkertilgangsmaskinen%3F)

For å kunne bruke dette biblioteket trenger appen din tilgang til Tilgangsmaskinen. Det innebærer at du ber om tilgang i
[#team-tilgangsmaskinen-værsågod](https://nav-it.slack.com/archives/C07GGDP38S2), og at du legger til en ny outbound rule:
```yaml
- application: populasjonstilgangskontroll
  namespace: tilgangsmaskin
```

I tillegg må appen din være en EntraID-app, og du må legge til følgende også innunder `spec`:
```yaml
azure:
  application:
    enabled: true
```
