Query
=====

Litt hjelp for å effektivisere bruken av `java.sql`, men ikke så mye mer enn det!

```kotlin
fun Connection.createName(name: String?) =
    prepareStatement("insert into name(name) values (?) returning id").use { stmt ->
        if (name == null) stmt.setObject(1, null) else stmt.setString(1, name)
        stmt.executeQuery().single { row -> row.getLong(1) }
    }

fun Connection.findName(id: Long) =
    prepareStatement("select name from name where id = ? limit 1").use { stmt ->
        stmt.setLong(1, id)
        stmt.executeQuery().singleOrNull { row -> row.getString("name") }
    }

fun main() = dataSource.connection.use { connection ->
    @Language("PostgreSQL")
    val sql = """create table name (
            id bigint primary key generated always as identity, 
            name text, 
            created timestamptz not null default now()
        )"""
    connection.createStatement().execute(sql)

    val (hansId, nullId) = connection.transaction {
        val hansId = connection.createName("hans")
        val nullId = connection.createName(null)
        Pair(hansId, nullId)
    }

    assertEquals("hans", connection.findName(hansId))
    assertEquals(null, connection.findName(nullId))
    try {
        connection.findName(1000)
    } catch (err: NoSuchElementException) {
        // raden finnes ikke
    }
    
    val mapName = { rs: ResultSet -> rs.getString("name") }
    val namesWithNull = connection.prepareStatement("select name from name").use {
        // map godtar null-rader
        it.executeQuery().map(mapName)
    }

    assertEquals(listOf("hans", null), namesWithNull)

    val namesWithoutNull = connection.prepareStatement("select name from name").use {
        // mapNotNull godtar ikke null-rader
        it.executeQuery().mapNotNull(mapName)
    }

    assertEquals(listOf("hans"), namesWithoutNull)
}
```

