# Texas Client

Enkel liten klient som bruker Texas sidecar'en for å hente access token for et gitt scope. Krever at man har spesifisert
i nais-manifestet at appen er en EntraID-app:
```yaml
spec:
  azure:
    application:
      enabled: true
```

Du kan lese mer om Texas i [nais-dokumentasjonen](https://doc.nav.cloud.nais.io/auth/explanations/#texas)
