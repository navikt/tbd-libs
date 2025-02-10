package com.github.navikt.tbd_libs.sql_dsl

import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

fun <R> DataSource.connection(block: Connection.() -> R): R {
    return connection.use(block)
}

// krever minst én rad og at mapping-funksjonen ikke returnerer null
fun <R> ResultSet.single(map: (ResultSet) -> R?): R {
    return checkNotNull(singleOrNull(map)) { "forventet ikke er null-verdi" }
}

// krever én rad, men mapping-funksjonen kan returnere null
fun <R> ResultSet.singleOrNull(map: (ResultSet) -> R?): R? {
    return this.map(map).single()
}

// returnerer null hvis resultatet er tomt eller mapping-funksjonen returnerer null
fun <R> ResultSet.firstOrNull(map: (ResultSet) -> R?): R? {
    return this.map(map).firstOrNull()
}

// siden flere av ResultSet-funksjonene returnerer potensielt null
// så føles det mer riktig å anta at map-funksjonen kan gi en nullable R.
// f.eks. vil ResultSet.getString() returnere `null` hvis kolonnen er lagret som `null` i databasen.
// i kotlin vil typen bli seende som `String!`, som kan godtas både som `String` og `String?` i kotlin.
// Det kan dessuten være legitimt bruksområde å hente ut rader, men bevare `null`-verdien. derfor foretas det ingen filtrering her.
// bruk `mapNotNull()` for å fjerne null-rader / gjøre listen not-null
fun <R> ResultSet.map(map: (ResultSet) -> R?): List<R?> {
    return buildList {
        while (next()) {
            add(map(this@map))
        }
    }
}

fun <R> ResultSet.mapNotNull(map: (ResultSet) -> R?): List<R> = map(map).filterNotNull()

fun <R> Connection.transaction(block: Connection.() -> R): R {
    return try {
        autoCommit = false
        block().also { commit() }
    } catch (err: Exception) {
        try {
            rollback()
        } catch (suppressed: Exception) {
            err.addSuppressed(suppressed)
        }
        throw err
    } finally {
        autoCommit = true
    }
}
