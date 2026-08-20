package no.nav.helse.speil.backend.app.testfixtures

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * Postgres-testcontainer + Flyway-migrering, til bruk i libbens egne integrasjonstester og i
 * spv-tester som ikke ønsker å dele en langlevende testdatabase.
 */
class TestDatabase private constructor(
    private val container: PostgreSQLContainer<*>,
    val dataSource: DataSource,
) {
    fun migrer(locations: List<String> = listOf("classpath:db/migration")) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .cleanDisabled(true)
            .load()
            .migrate()
    }

    fun stop() {
        (dataSource as? HikariDataSource)?.close()
        container.stop()
    }

    companion object {
        fun start(): TestDatabase {
            @Suppress("RESOURCE")
            val container = PostgreSQLContainer("postgres:18-alpine").apply { start() }
            val dataSource =
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = container.jdbcUrl
                        username = container.username
                        password = container.password
                        maximumPoolSize = 3
                    },
                )
            return TestDatabase(container, dataSource)
        }
    }
}
