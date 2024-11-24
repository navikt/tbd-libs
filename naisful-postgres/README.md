Naisful Postgres
================

Enkel måte å komme kjapt i gang med en databasetilkobling.

Auto-detecter miljøvariabler og lager JDBC-url for deg.

## Fra miljøvariabler
```kotlin

// lager jdbc-url fra env
val jdbcUrl = defaultJdbcUrl()
```

Miljøvariabler som slutter på `_HOST`, `_USERNAME` osv. vil bli brukt.
Hvis appen har flere slike miljøvariabler så kan de skilles ved å spesifisere prefix:
```kotlin
ConnectionConfigFactory.Env(envVarPrefix = "DB")
```
Da vil det søkes etter `DB_HOST`, `DB_USERNAME`, osv.

## Fra mount path
Hvis du vil laste sql secrets fra mount path så kan de konfigureres slik i nais.yml. 
```yml
  filesFrom:
    - secret: google-sql-APP
      mountPath: /var/run/secrets/sql/APP
```
og så gjøre dette i kotlin:
```kotlin
// lager jdbc-url fra mount path
val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.MountPath("/var/run/secrets/sql/APP"))
```

## Google SocketFactory

Det er også en variant som vil konfe opp `com.google.cloud.sql.postgres.SocketFactory` i JDBC-url:
```kotlin
val jdbcUrl = jdbcUrlWithGoogleSocketFactory("dbinstance", ConnectionConfigFactory.Env())
```