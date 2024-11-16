package com.github.navikt.tbd_libs.test_support

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import org.flywaydb.core.Flyway
import java.sql.Connection

class TestDataSource(
    private val dbnavn: String,
    config: HikariConfig,
    private val cleanupStrategy: CleanupStrategy? = null, // komma-separert liste over tabeller som skal tømmes
    private val initStrategy: InitStrategy? = null,
    maxHikariPoolSize: Int = 2 // hvor stor hikari-poolen skal være
) {
    private val migrationConfig = HikariConfig()
    private val appConfig = HikariConfig()
    private val dataSource: HikariDataSource by lazy { HikariDataSource(appConfig) }
    private val migrationDataSource: HikariDataSource by lazy { HikariDataSource(migrationConfig) }

    private val flyway by lazy {
        Flyway.configure()
            .dataSource(migrationDataSource)
            .validateMigrationNaming(true)
            .cleanDisabled(false)
            .load()
    }

    init {
        println("Oppretter datasource med dbnavn=$dbnavn")
        config.copyStateTo(migrationConfig)
        config.copyStateTo(appConfig)

        migrationConfig.maximumPoolSize = 2 // flyway klarer seg ikke med én connection visstnok
        migrationConfig.initializationFailTimeout = Duration.ofSeconds(20).toMillis()

        appConfig.maximumPoolSize = maxHikariPoolSize
    }

    val ds: HikariDataSource by lazy {
        migrate()
        dataSource
    }

    private fun migrate() {
        initStrategy?.apply {
            println("Initialiserer $dbnavn med $this")
            migrationDataSource.connection.use { connection ->
                init(connection)
            }
        }
        println("Migrerer dbnavn=$dbnavn")
        flyway.migrate()
    }

    fun cleanUp() {
        println("Rydder opp og forbereder gjenbruk i $dbnavn")
        if (cleanupStrategy == null) {
            println("cleanUpTables er ikke spesifisert, re-migrerer derfor med Flyway")
            flyway.clean()
            flyway.migrate()
            return
        }
        println("Tømmer tabellene $cleanupStrategy")
        migrationDataSource.connection.use {
            cleanupStrategy.cleanup(it)
        }
    }
    fun teardown(dropDatabase: (String) -> Unit) {
        migrationDataSource.close()
        dataSource.close()
        dropDatabase(dbnavn)
    }
}

fun interface InitStrategy {
    fun init(connection: Connection)
}

fun interface CleanupStrategy {
    fun cleanup(connection: Connection)

    companion object {
        // comma-separated list of tables
        fun tables(tables: String): CleanupStrategy {
            return CleanupStrategy {
                it.createStatement().execute("truncate table $tables restart identity cascade;")
            }
        }
    }
}