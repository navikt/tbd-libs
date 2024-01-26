package com.github.navikt.tbd_libs.test_support

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DatabaseContainersTest {
    private companion object {
        private val databaseContainer = DatabaseContainers.container("tbd-libs")
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