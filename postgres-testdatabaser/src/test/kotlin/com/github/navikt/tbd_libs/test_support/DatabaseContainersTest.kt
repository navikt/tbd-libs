package com.github.navikt.tbd_libs.test_support

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration

class DatabaseContainersTest {
    private companion object {
        private const val MAX_POOL_SIZE = 4
        private val databaseContainer = DatabaseContainers.container("tbd-libs", databasePoolSize = MAX_POOL_SIZE)

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
    fun `tømmer bassenget`() {
        val tilkoblinger = (1..< MAX_POOL_SIZE).map { databaseContainer.nyTilkobling() }
        assertThrows<RuntimeException> { databaseContainer.nyTilkobling(Duration.ofMillis(10)) }
        tilkoblinger.forEach { databaseContainer.droppTilkobling(it) }
    }

    @Test
    fun `får tilkobling`() {
        @Language("PostgreSQL")
        val table = "create table foo(id bigserial primary key, name varchar not null);"
        val connection = testDataSource.ds.connection
        connection.createStatement().execute(table)
        connection.createStatement().execute("insert into foo(name) values('foobar');")
        val resultSet = connection.createStatement().executeQuery("select count(1) from foo")
        assertTrue(resultSet.next())
        val antall = resultSet.getInt(1)
        assertEquals(1, antall)
    }
}