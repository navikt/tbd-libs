package com.github.navikt.tbd_libs.test_support

import com.zaxxer.hikari.HikariConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class DatabaseContainer(
    private val appnavn: String,
    private val poolSize: Int,
    private val cleanupStrategy: CleanupStrategy? = null,
    private val initStrategy: InitStrategy? = null,
    private val maxHikariPoolSize: Int = 2,
    private val walLevelLogical: Boolean = false
) {
    private val instance by lazy {
        PostgreSQLContainer("postgres:15").apply {
            withCreateContainerCmdModifier { command -> command.withName(appnavn) }
            if (walLevelLogical) {
                // Cloud SQL har wal_level = 'logical' på grunn av flagget cloudsql.logical_decoding i
                // naiserator.yaml. Vi må sette det samme lokalt for at flyway migrering skal fungere.
                withCommand("postgres", "-c", "wal_level=logical")
            }
            withReuse(true)
            withLabel("app-navn", appnavn)
            DockerClientFactory.lazyClient().apply {
                this
                    .listContainersCmd()
                    .exec()
                    .filter { it.labels["app-navn"] == appnavn }
                    .forEach {
                        killContainerCmd(it.id).exec()
                        removeContainerCmd(it.id).withForce(true).exec()
                    }
            }
            start()
        }
    }

    private val systemtilkobling by lazy { instance.createConnection("") }
    private val tilgjengeligeTilkoblinger by lazy {
        ArrayBlockingQueue(poolSize, false, opprettTilkoblinger(cleanupStrategy, initStrategy, maxHikariPoolSize))
    }

    fun nyTilkobling(timeout: Duration = Duration.ofSeconds(20)): TestDataSource {
        return tilgjengeligeTilkoblinger.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) ?: tilkoblingIkkeTilgjengelig(timeout)
    }

    private fun tilkoblingIkkeTilgjengelig(timeout: Duration): Nothing {
        throw RuntimeException("Ventet i ${timeout.toMillis()} millisekunder uten å få en ledig database")
    }

    private fun opprettTilkoblinger(cleanupStrategy: CleanupStrategy?, initStrategy: InitStrategy?, maxHikariPoolSize: Int) = runBlocking(Dispatchers.IO) {
        (1..poolSize)
            .map { async { opprettTilkobling("testdb_$it", cleanupStrategy, initStrategy, maxHikariPoolSize) } }
            .awaitAll()
    }

    private fun opprettTilkobling(dbnavn: String, cleanupStrategy: CleanupStrategy?, initStrategy: InitStrategy?, maxHikariPoolSize: Int): TestDataSource {
        opprettDatabase(dbnavn)
        instance.withDatabaseName(dbnavn)
        return TestDataSource(dbnavn, HikariConfig().apply {
            username = instance.username
            password = instance.password
            jdbcUrl = instance.jdbcUrl
        }, cleanupStrategy, initStrategy, maxHikariPoolSize)
    }

    private fun opprettDatabase(dbnavn: String) {
        println("Oppretter databasen $dbnavn")
        systemtilkobling.createStatement().execute("create database $dbnavn")
    }

    fun droppTilkobling(testDataSource: TestDataSource) {
        println("Tilgjengeliggjør datbasen igjen")
        testDataSource.cleanUp()
        check(tilgjengeligeTilkoblinger.offer(testDataSource)) {
            "Kunne ikke returnere tilkoblingen"
        }
    }

    fun ryddOpp() {
        tilgjengeligeTilkoblinger.forEach {
            it.teardown { dbnavn ->
                println("Dropper databasen $dbnavn")
                systemtilkobling.createStatement().execute("drop database $dbnavn")
            }
        }
    }
}
