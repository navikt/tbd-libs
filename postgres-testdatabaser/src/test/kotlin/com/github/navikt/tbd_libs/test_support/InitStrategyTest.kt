package com.github.navikt.tbd_libs.test_support

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InitStrategyTest {
    private companion object {
        private const val MAX_POOL_SIZE = 4

        private val initStrategy = InitStrategy {
            it.createStatement().use { statement ->
                statement.execute("create user unit_test_user;")
            }
        }
        private val cleanUpStrategy = CleanupStrategy {
            it.createStatement().use { statement ->
                statement.execute("drop user unit_test_user;")
            }
        }
        private val databaseContainer = DatabaseContainers.container("tbd-libs-psql-init-test", cleanupStrategy = cleanUpStrategy, initStrategy = initStrategy, databasePoolSize = MAX_POOL_SIZE)

        @AfterAll
        @JvmStatic
        fun remove() {
            databaseContainer.ryddOpp()
        }
    }

    private lateinit var testDataSource: TestDataSource

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun teardown() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    @Test
    fun `bruker finnes`() {
        val finnes = testDataSource.ds.connection.createStatement().use { statement ->
            statement.execute("SELECT 1 FROM pg_roles WHERE rolname='unit_test_user';")
            statement.resultSet.use { resultSet ->
                resultSet.next()
                resultSet.getBoolean(1)
            }
        }
        assertTrue(finnes)
    }
}