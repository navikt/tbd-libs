package no.nav.helse.speil.backend.app.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.MeterRegistry

fun DatabaseConfig.dataSource(meterRegistry: MeterRegistry? = null): HikariDataSource {
    val defaults = hikariDefaults
    return HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = this@dataSource.jdbcUrl
            maximumPoolSize = defaults.maximumPoolSize
            minimumIdle = defaults.minimumIdle
            idleTimeout = defaults.idleTimeout.toMillis()
            maxLifetime = defaults.maxLifetime.toMillis()
            connectionTimeout = defaults.connectionTimeout.toMillis()
            initializationFailTimeout = defaults.initializationFailTimeout.toMillis()
            leakDetectionThreshold = defaults.leakDetectionThreshold.toMillis()
            poolName = "speil-backend-app"
            if (meterRegistry != null) {
                this.metricRegistry = meterRegistry
            }
        },
    )
}
