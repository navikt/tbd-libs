package com.github.navikt.tbd_libs.sql_dsl

import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryTest {

    @Test
    fun `single returnerer én ikke-null rad, kaster exception hvis ikke`() = setupTest { connection ->
        val hansId = connection.createName("hans")
        val nullId = connection.createName(null)

        val mapName = { rs: ResultSet -> rs.getString("name") }
        assertEquals("hans", connection.name(hansId).single(mapName))
        assertThrows<IllegalStateException> { assertEquals(null, connection.name(nullId).single(mapName)) }
        assertThrows<NoSuchElementException> { assertEquals(null, connection.name(1000).single(mapName)) }
    }

    @Test
    fun `singleOrNull returnerer én potensielt null-rad, kaster exception ved tomt resultat`() = setupTest { connection ->
        val hansId = connection.createName("hans")
        val nullId = connection.createName(null)

        val mapName = { rs: ResultSet -> rs.getString("name") }
        assertEquals("hans", connection.name(hansId).singleOrNull(mapName))
        assertEquals(null, connection.name(nullId).singleOrNull(mapName))
        assertThrows<NoSuchElementException> { connection.name(1000).singleOrNull(mapName) }
    }

    @Test
    fun `firstOrNull returnerer potensiell null-rad hvis den finnes, null ellers`() = setupTest { connection ->
        val hansId = connection.createName("hans")
        val nullId = connection.createName(null)

        val mapName = { rs: ResultSet -> rs.getString("name") }
        assertEquals("hans", connection.name(hansId).firstOrNull(mapName))
        assertEquals(null, connection.name(nullId).firstOrNull(mapName))
        assertEquals(null, connection.name(1000).firstOrNull(mapName))
    }

    @Test
    fun `map omformer hver rad, godtar at resultatet er null`() = setupTest { connection ->
        connection.createName("hans")
        connection.createName(null)

        val mapName = { rs: ResultSet -> rs.getString("name") }
        val names = connection.prepareStatement("select name from name").use {
            it.executeQuery().map(mapName)
        }

        assertEquals(listOf("hans", null), names)
    }

    @Test
    fun `mapNoptNull omformer hver ikke-nulll rad`() = setupTest { connection ->
        connection.createName("hans")
        connection.createName(null)

        val mapName = { rs: ResultSet -> rs.getString("name") }
        val names = connection.prepareStatement("select name from name").use {
            it.executeQuery().mapNotNull(mapName)
        }

        assertEquals(listOf("hans"), names)
    }

    @Test
    fun `transaction ruller tilbake ved feil`() = setupTest { connection ->
        assertThrows<IllegalStateException> {
            connection.transaction {
                connection.createName("hans")
                error("something went wrong")
            }
        }
        assertEquals(emptyList<Any>(), connection.names())

        assertTrue(connection.autoCommit) { "transaction må sette autoCommit tilbake" }
        connection.createName("hans")
        assertEquals(listOf("hans"), connection.names())
    }

    @Test
    fun `transaction committer hvis alt er ok`() = setupTest { connection ->
        connection.transaction { connection.createName("hans") }
        assertEquals(listOf("hans"), connection.names())
    }

    private fun Connection.names(): List<String?> {
        val mapName = { rs: ResultSet -> rs.getString("name") }
        return prepareStatement("select name from name").use { it.executeQuery().map(mapName) }
    }

    private fun Connection.name(id: Long) =
        prepareStatement("select name from name where id = ? limit 1").let { stmt ->
            stmt.setLong(1, id)
            stmt.executeQuery()
        }

    private fun Connection.createName(name: String?) =
        prepareStatement("insert into name(name) values (?) returning id").use { stmt ->
            if (name == null) stmt.setObject(1, null) else stmt.setString(1, name)
            stmt.executeQuery().single { row -> row.getLong(1) }
        }

    private fun Connection.createTestTable() {
        @Language("PostgreSQL")
        val sql = """create table name (
            id bigint primary key generated always as identity, 
            name text, 
            created timestamptz not null default now()
        )"""
        createStatement().execute(sql)
    }

    private fun setupTest(testblokk: (Connection) -> Unit) {
        dbTest { db ->
            db.connection.use { connection ->
                connection.createTestTable()
                testblokk(connection)
            }
        }
    }
}

private val databaseContainer = DatabaseContainers.container("sql-dsl")
fun dbTest(testblokk: (DataSource) -> Unit) {
    val testDataSource = databaseContainer.nyTilkobling()
    try {
        testblokk(testDataSource.ds)
    } finally {
        databaseContainer.droppTilkobling(testDataSource)
    }
}
