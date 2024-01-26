package com.github.navikt.tbd_libs.test_support

import com.zaxxer.hikari.HikariConfig
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class DatabaseContainer(
    private val appnavn: String,
    private val poolSize: Int,
    private val cleanUpTables: String? = null,
    private val maxHikariPoolSize: Int = 2
) {
    private val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:15").apply {
            withCreateContainerCmdModifier { command -> command.withName(appnavn) }
            withReuse(true)
            withLabel("app-navn", appnavn)
            start()
        }
    }

    private val systemtilkobling by lazy { instance.createConnection("") }
    private val tilgjengeligeTilkoblinger by lazy {
        ArrayBlockingQueue(poolSize, false, opprettTilkoblinger(cleanUpTables, maxHikariPoolSize))
    }

    fun nyTilkobling(): TestDataSource {
        return tilgjengeligeTilkoblinger.poll(20, TimeUnit.SECONDS) ?: throw RuntimeException("Ventet i 20 sekunder uten å få en ledig database")
    }

    private fun opprettTilkoblinger(cleanUpTables: String?, maxHikariPoolSize: Int) =
        (1..poolSize).map { opprettTilkobling("testdb_$it", cleanUpTables, maxHikariPoolSize) }

    private fun opprettTilkobling(dbnavn: String, cleanUpTables: String? = null, maxHikariPoolSize: Int): TestDataSource {
        opprettDatabase(dbnavn)
        instance.withDatabaseName(dbnavn)
        return TestDataSource(dbnavn, HikariConfig().apply {
            username = instance.username
            password = instance.password
            jdbcUrl = instance.jdbcUrl
        }, cleanUpTables, maxHikariPoolSize)
    }

    private fun opprettDatabase(dbnavn: String) {
        println("Oppretter databasen $dbnavn")
        systemtilkobling.createStatement().execute("create database $dbnavn")
    }

    fun droppTilkobling(testDataSource: TestDataSource) {
        println("Tilgjengeliggjør datbasen igjen")
        testDataSource.cleanUp()
        tilgjengeligeTilkoblinger.offer(testDataSource)
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