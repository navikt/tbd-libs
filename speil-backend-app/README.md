# speil-backend-app

Bibliotek som samler felles oppsett for Speil-backend-apper i tbd: Ktor-server, Azure AD-autentisering, tilgangsstyring via Tilgangsmaskinen, Valkey, PostgreSQL og Rapids & Rivers.

## Krav til appen som bruker biblioteket

### Nais-konfigurasjon

```yaml
azure:
  application:
    enabled: true
    claims:
      extra:
        - NAVident
      groups:
        - id: <ENTRAID_UUID_SPEIL_LESETILGANG>   # TILGANG_LES
        - id: <ENTRAID_UUID_SPEIL_SAKSBEHANDLER> # TILGANG_SKRIV

valkey:
  - instance: personpseudoid
    access: read

gcp:
  sqlInstances:
    - type: POSTGRES_18
      databases:
        - name: <app-navn>

kafka:
  pool: <nav-prod / nav-dev>

env:
  - name: TILGANG_LES
    value: <ENTRAID_UUID_SPEIL_LESETILGANG>
  - name: TILGANG_SKRIV
    value: <ENTRAID_UUID_SPEIL_SAKSBEHANDLER>
  - name: TILGANGSMASKINEN_SCOPE
    value: <scope>
  - name: TILGANGSMASKINEN_BASE_URL
    value: http://populasjonstilgangskontroll.tilgangsmaskin

accessPolicy:
  outbound:
    rules:
      - application: populasjonstilgangskontroll
        namespace: tilgangsmaskin
```

Kafka read/write mot `tbd.rapid.v1` legges inn i [spleis-repo](https://github.com/navikt/helse-spleis).

### Tilgang til Tilgangsmaskinen

Be om tilgang i Slack-kanalen **#team-tilgangsmaskinen-værsågod** med følgende format:

**Dev:**
```yaml
- application: <app-navn>
  namespace: tbd
  cluster: dev-gcp
```

**Prod:**
```yaml
- application: <app-navn>
  namespace: tbd
  cluster: prod-gcp
```

Eksempel for `sp-vilkarsproving`:
```yaml
- application: sp-vilkarsproving
  namespace: tbd
  cluster: dev-gcp
- application: sp-vilkarsproving
  namespace: tbd
  cluster: prod-gcp
```

## Bruk

```kotlin
fun main() {
    val konfigurasjon = AppKonfigurasjon.fraEnv(appNavn = "min-app")
    startApp(konfigurasjon) { app ->
        // registrer routes her
    }
}
```
