package com.github.navikt.tbd_libs.test_support

import java.util.concurrent.ConcurrentHashMap

object DatabaseContainers {
    private val JUNIT_PARALLELISM = System.getProperty("junit.jupiter.execution.parallel.config.fixed.parallelism")?.toInt() ?: 1

    private const val MIN_POOL_SIZE = 1
    private val MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors()
    private val POOL_SIZE = minOf(MAX_POOL_SIZE, maxOf(MIN_POOL_SIZE, JUNIT_PARALLELISM))

    private val instances = ConcurrentHashMap<String, DatabaseContainer>()

    // gjenbruker containers med samme navn for å unngå
    // å spinne opp mange containers
    fun container(appnavn: String, cleanUpTables: CleanupStrategy? = null, maxHikariPoolSize: Int = 2, databasePoolSize: Int = POOL_SIZE, walLevelLogical: Boolean = false): DatabaseContainer {
        return instances.getOrPut(appnavn) {
            DatabaseContainer(appnavn, databasePoolSize, cleanUpTables, maxHikariPoolSize, walLevelLogical)
        }
    }
}