package no.nav.helse.speil.backend.app.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.speil.backend.app.logging.loggInfo
import org.flywaydb.core.Flyway

fun migrerSynkront(config: DatabaseConfig) {
    loggInfo("Migrerer database")
    val migreringsDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                maximumPoolSize = 2
                minimumIdle = 1
                poolName = "speil-backend-app-migrering"
            },
        )
    migreringsDataSource.use { migreringsDataSource ->
        Flyway
            .configure()
            .dataSource(migreringsDataSource)
            .locations(*config.flywayLocations.toTypedArray())
            .cleanDisabled(true)
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }
    loggInfo("Migrering ferdig")
}
