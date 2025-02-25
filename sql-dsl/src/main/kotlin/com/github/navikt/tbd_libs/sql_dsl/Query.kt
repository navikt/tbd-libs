package com.github.navikt.tbd_libs.sql_dsl

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

@OptIn(ExperimentalContracts::class)
private fun <T : Any> checkNotNull(columnIdentifier: Any, value: T?): T {
    contract {
        returns() implies (value != null)
    }
    return checkNotNull(value) { "Column <$columnIdentifier> was null." }
}

fun ResultSet.stringOrNull(columnName: String): String? = getString(columnName)
fun ResultSet.string(columnName: String): String = checkNotNull(columnName, stringOrNull(columnName))
fun ResultSet.stringOrNull(column: Int): String? = getString(column)
fun ResultSet.string(column: Int): String = checkNotNull(column, stringOrNull(column))

fun ResultSet.longOrNull(columnName: String): Long? = getLong(columnName)
fun ResultSet.long(columnName: String): Long = checkNotNull(columnName, longOrNull(columnName))
fun ResultSet.longOrNull(column: Int): Long? = getLong(column)
fun ResultSet.long(column: Int): Long = checkNotNull(column, longOrNull(column))

fun ResultSet.intOrNull(columnName: String): Int? = getInt(columnName)
fun ResultSet.int(columnName: String): Int = checkNotNull(columnName, intOrNull(columnName))
fun ResultSet.intOrNull(column: Int): Int? = getInt(column)
fun ResultSet.int(column: Int): Int = checkNotNull(column, intOrNull(column))

fun ResultSet.doubleOrNull(columnName: String): Double? = getDouble(columnName)
fun ResultSet.double(columnName: String): Double = checkNotNull(columnName, doubleOrNull(columnName))
fun ResultSet.doubleOrNull(column: Int): Double? = getDouble(column)
fun ResultSet.double(column: Int): Double = checkNotNull(column, doubleOrNull(column))

fun ResultSet.booleanOrNull(columnName: String): Boolean? = getBoolean(columnName)
fun ResultSet.boolean(columnName: String): Boolean = checkNotNull(columnName, booleanOrNull(columnName))
fun ResultSet.booleanOrNull(column: Int): Boolean? = getBoolean(column)
fun ResultSet.boolean(column: Int): Boolean = checkNotNull(column, booleanOrNull(column))

fun ResultSet.uuidOrNull(columnName: String): UUID? = getObject(columnName, UUID::class.java)
fun ResultSet.uuid(columnName: String): UUID = checkNotNull(columnName, uuidOrNull(columnName))
fun ResultSet.uuidOrNull(column: Int): UUID? = getObject(column, UUID::class.java)
fun ResultSet.uuid(column: Int): UUID = checkNotNull(column, uuidOrNull(column))

/*
    relevant dokumentasjon: https://jdbc.postgresql.org/documentation/query/#using-java-8-date-and-time-classes

    TIMESTAMP bør mappes til/fra LocalDateTime siden postgres ikke gjør noen tolkning av tidssone.
            hvis du vet at du kun inserter utc så kan du i teorien hente ut en OffsetDateTime fra kolonnen siden fravær av tidssone tolkes som UTC.
            men dette er en ganske svak antagelse.

            tl;dr: lagre ALT med timestamptz og Instant/OffsetDateTime så slipper du å tenke mer på det!

    TIMESTAMPTZ (TIMESTAMP WITH TIME ZONE) bør mappes til/fra OffsetDateTime (som i tur kan tolkes som Instant osv)
 */
fun ResultSet.offsetDateTimeOrNull(columnName: String): OffsetDateTime? = getObject(columnName, OffsetDateTime::class.java)
fun ResultSet.offsetDateTime(columnName: String): OffsetDateTime = checkNotNull(offsetDateTimeOrNull(columnName))
fun ResultSet.offsetDateTimeOrNull(column: Int): OffsetDateTime? = getObject(column, OffsetDateTime::class.java)
fun ResultSet.offsetDateTime(column: Int): OffsetDateTime = checkNotNull(offsetDateTimeOrNull(column))

fun ResultSet.localDateTimeOrNull(columnName: String): LocalDateTime? = getObject(columnName, LocalDateTime::class.java)
fun ResultSet.localDateTime(columnName: String): LocalDateTime = checkNotNull(localDateTimeOrNull(columnName))
fun ResultSet.localDateTimeOrNull(column: Int): LocalDateTime? = getObject(column, LocalDateTime::class.java)
fun ResultSet.localDateTime(column: Int): LocalDateTime = checkNotNull(localDateTimeOrNull(column))

fun ResultSet.localDateOrNull(columnName: String): LocalDate? = getObject(columnName, LocalDate::class.java)
fun ResultSet.localDate(columnName: String): LocalDate = checkNotNull(localDateOrNull(columnName))
fun ResultSet.localDateOrNull(column: Int): LocalDate? = getObject(column, LocalDate::class.java)
fun ResultSet.localDate(column: Int): LocalDate = checkNotNull(localDateOrNull(column))

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

fun Connection.prepareStatementWithNamedParameters(sql: String, parametersBlock: ParametersBuilder.() -> Unit): PreparedStatement {
    val (query, orderOfNamedParameters) = extractNamedParametersFromQuery(sql)
    val parameters = ParametersBuilder(this).apply(parametersBlock).build()
    val remainingParameters = orderOfNamedParameters.toSet() - parameters.keys
    require(remainingParameters.isEmpty()) {
        "følgende parametre er ikke blitt spesifisert: $remainingParameters"
    }
    return buildPreparedStatement(prepareStatement(query), orderOfNamedParameters.map { parameters.getValue(it) })
}

private fun buildPreparedStatement(stmt: PreparedStatement, orderOfNamedParameters: List<PreparedStatement.(Int) -> Unit>): PreparedStatement {
    orderOfNamedParameters.forEachIndexed { index, valueSetter ->
        val col = index + 1
        valueSetter(stmt, col)
    }

    require(stmt.parameterMetaData.parameterCount == orderOfNamedParameters.size) {
        "det er ulikt antall parametre i prepared query vs. navngitte parametre. Har du blandet bruk av ? og :parameternavn i spørringen?"
    }
    return stmt
}

private val namedParameterRegex = Regex(":([\\p{L}_-]+)")

internal fun extractNamedParametersFromQuery(sql: String): Pair<String, List<String>> {
    val query = sql.replace(namedParameterRegex, "?")
    val orderOfNamedParameters: List<String> = namedParameterRegex.findAll(sql)
        .map { match -> match.groupValues.last() }
        .toList()
    return query to orderOfNamedParameters
}

data class ParametersBuilder(
    private val connection: Connection,
    private val namedValues: MutableMap<String, PreparedStatement.(column: Int) -> Unit> = mutableMapOf()
) {
    fun withNull(name: String) {
        withParameter(name) { setObject(it, null) }
    }

    fun withParameter(name: String, value: String) {
        withParameter(name) { setString(it, value) }
    }

    fun withParameter(name: String, value: Boolean) {
        withParameter(name) { setBoolean(it, value) }
    }

    fun withParameter(name: String, value: Int) {
        withParameter(name) { setInt(it, value) }
    }

    fun withParameter(name: String, value: Long) {
        withParameter(name) { setLong(it, value) }
    }

    fun withParameter(name: String, value: Double) {
        withParameter(name) { setDouble(it, value) }
    }

    fun withParameter(name: String, value: UUID) {
        withParameter(name) { setObject(it, value) }
    }

    /**
     * passende kolonne-type i postgres: timestamp eller timestamptz.
     *
     * siden Instant alltid er UTC-tid så er det ett fett hvilken kolonne som brukes.
     */
    fun withParameter(name: String, value: Instant) {
        withParameter(name) { setObject(it, value, Types.OTHER) }
    }

    /**
     * passende kolonne-type i postgres: timestamptz
     *
     * Hvordan fungerer TIMESTAMP WITH TIME ZONE i PostgreSQL?
     *
     * PostgreSQL lagrer ikke faktisk tidssonen, men normaliserer tidspunktet til UTC.
     * Når du setter inn en TIMESTAMP WITH TIME ZONE, vil PostgreSQL konvertere verdien til UTC hvis den har en tidssone.
     * Når du henter verdien, konverteres den tilbake til din klients tidssone (som standard er systemets tidssone).
     *
     * TIMESTAMP WITH TIME ZONE lagrer derfor ikke tidssonen, den bruker den kun til å konvertere innkommende verdier til UTC.
     *
     *
     * TL;DR:
     * - timestamp-kolonnen lagrer tidspunktene rått uten konvertering.
     *      hvis du kun inserter ting med én tidssone så kan du "late som" at
     *      kolonnen lagrer tidspunktene dine riktig
     * - timestamp with time zone konverterer tidspunktet til utc før lagring
     *
     * når vi henter frem tidspunktene så vil:
     * - timestamp-kolonnen vises akkurat slik det ble satt inn
     * - timestamp with time zone vil vises i den tidssonen klienten har satt
     */
    fun withParameter(name: String, value: ZonedDateTime) {
        withParameter(name, value.toInstant())
    }

    /**
     * passende kolonne-type i postgres: timestamptz
     */
    fun withParameter(name: String, value: OffsetDateTime) {
        withParameter(name, value.toInstant())
    }

    fun withParameter(name: String, value: LocalDate) {
        withParameter(name) { setObject(it, value, Types.DATE) }
    }

    inline fun <reified T> withParameter(name: String, value: List<T>) {
        val sqlType = when (T::class) {
            String::class -> "TEXT"
            Long::class -> "BIGINT"
            Int::class -> "INT"
            else -> error("støtter kun lister av typen String, Long eller Int")
        }
        withParameter(name) { setArray(it, connection.createArrayOf(sqlType, value.toTypedArray())) }
    }

    @LocalDateTimeIDatabase(
        message = "LocalDateTime representerer ikke et forenlig punkt i tid og bør unngås til bruk i databaser. " +
            "Bruk heller Instant og en timestamptz-kolonne (timestamp with time zone)! " +
            "Hvis du bruker denne funksjonen så vil verdien antas å være i systemets/maskinens tidssone! " +
            "Dersom applikasjonen og databasen kjører i samme tidssone så vil det se ut til å fungere, helt til premisset endres."
    )
    fun withParameter(name: String, value: LocalDateTime) {
        val konvertertTid = value.atZone(ZoneId.systemDefault()).toInstant()
        withParameter(name, konvertertTid)
    }

    fun withParameter(name: String, valueSetter: PreparedStatement.(column: Int) -> Unit) = apply {
        require(null == namedValues.putIfAbsent(name, valueSetter)) {
            "<$name> har blitt satt som parameter tidligere"
        }
    }

    fun build() = namedValues.toMap()
}

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class LocalDateTimeIDatabase(val message: String)
