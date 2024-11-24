package com.github.navikt.tbd_libs.naisful.postgres

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.plus
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText


/**
 * bruker _JDBC_URL hvis den er satt, ellers bygges opp en jdbc-url:
 * @see <a href="https://doc.nais.io/persistence/postgres/reference/?h=jdbc#database-connnection">nais doc</a>
 */
fun defaultJdbcUrl(metode: ConnectionConfigFactory = ConnectionConfigFactory.Env(), options: Map<String, String> = emptyMap()): String? {
    return metode.buildJdbcUrl(options)
}

/**
 * bruker _JDBC_URL hvis den er satt, ellers bygges opp en jdbc-url med gitte options.
 * det betyr at socketFactory kun brukes hvis _JDBC_URL ikke finnes
 *
 * @see <a href="https://doc.nais.io/persistence/postgres/reference/?h=jdbc#database-connnection">nais doc</a>
 */
fun jdbcUrlWithGoogleSocketFactory(databaseInstance: String, metode: ConnectionConfigFactory, gcpTeamProjectId: String = System.getenv("GCP_TEAM_PROJECT_ID"), databaseRegion: String = "europe-north1"): String? {
    return defaultJdbcUrl(metode, mapOf(
        "socketFactory" to "com.google.cloud.sql.postgres.SocketFactory",
        "cloudSqlInstance" to "$gcpTeamProjectId:$databaseRegion:$databaseInstance"
    ))
}

sealed class ConnectionConfigFactory {
    abstract fun buildJdbcUrl(options: Map<String, String>): String?


    data class Env(val env: Map<String, String> = System.getenv(), val envVarPrefix: String? = null) : ConnectionConfigFactory() {
        override fun buildJdbcUrl(options: Map<String, String>): String? {
            return buildJdbcUrl(options) { key ->
                env.getKeySuffixOrNull(envVarPrefix, key)
            }
        }
    }
    data class MountPath(val path: String) : ConnectionConfigFactory() {
        override fun buildJdbcUrl(options: Map<String, String>): String? {
            val secretsPath = Path(path).listDirectoryEntries()
            return buildJdbcUrl(options) { key ->
                secretsPath.firstOrNull { it.name.endsWith(key) }?.readText()
            }
        }
    }

    private companion object {
        private fun buildJdbcUrl(options: Map<String, String>, strategi: (String) -> String?): String? {
            val jdbcUrlFromPlatform = strategi("_JDBC_URL")
            if (jdbcUrlFromPlatform != null) return jdbcUrlFromPlatform

            val hostname = strategi("_HOST") ?: return null
            val port = strategi("_PORT")?.toInt() ?: return null
            val databaseName = strategi("_DATABASE") ?: return null
            val username = strategi("_USERNAME") ?: return null
            val password = strategi("_PASSWORD") ?: return null

            val sslOptions = buildMap {
                strategi("_SSLCERT")?.also { this["sslcert"] = it }
                strategi("_SSLROOTCERT")?.also { this["sslrootcert"] = it }
                strategi("_SSLKEY_PK8")?.also { this["sslkey"] = it }
                strategi("_SSLMODE")?.also { this["sslmode"] = it }
            }

            return buildPostgresCompliantJdbcUrl(hostname, port, databaseName, username, password, options + sslOptions)
        }
    }
}

fun Map<String, String>.getKeySuffixOrNull(prefix: String?, suffix: String): String? {
    val searchKey = "${prefix ?: ""}$suffix"
    return entries.firstOrNull { (k, _) -> k.endsWith(searchKey) }?.value
}

private fun buildPostgresCompliantJdbcUrl(hostname: String, port: Int, databaseName: String, username: String, password: String, options: Map<String, String> = emptyMap()): String {
    val defaultOptions = mapOf(
        "user" to username,
        "password" to password
    )
    val optionsString = optionsString(defaultOptions + options)
    return "jdbc:postgresql://$hostname:$port/$databaseName?$optionsString"
}

private fun optionsString(options: Map<String, String>): String {
    return (options).entries.joinToString(separator = "&") { (k, v) -> "$k=$v" }
}