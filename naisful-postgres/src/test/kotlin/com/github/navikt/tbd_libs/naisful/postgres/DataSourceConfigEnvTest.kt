package com.github.navikt.tbd_libs.naisful.postgres

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataSourceConfigEnvTest {

    @Test
    fun `default jdbc url`() {
        val fakeEnv = mapOf(
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "postgres",
            "DB_USERNAME" to "username",
            "DB_PASSWORD" to "secret",
        )
        val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.Env(fakeEnv))
        assertEquals("jdbc:postgresql://localhost:5432/postgres?user=username&password=secret", jdbcUrl)
    }

    @Test
    fun `default jdbc url - jdbc_url set`() {
        val fakeEnv = mapOf(
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "postgres",
            "DB_USERNAME" to "username",
            "DB_PASSWORD" to "secret",
            "DB_JDBC_URL" to "jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar",
        )
        val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.Env(fakeEnv))
        assertEquals("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar", jdbcUrl)
    }

    @Test
    fun `default jdbc url - env with prefix`() {
        val fakeEnv = mapOf(
            "CONFLICTING_HOST" to "remote_ip",
            "CONFLICTING_PORT" to "2345",
            "CONFLICTING_DATABASE" to "dev-db",
            "CONFLICTING_USERNAME" to "willy",
            "CONFLICTING_PASSWORD" to "wonka",

            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "postgres",
            "DB_USERNAME" to "username",
            "DB_PASSWORD" to "secret",
        )
        val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.Env(fakeEnv, envVarPrefix = "DB"))
        assertEquals("jdbc:postgresql://localhost:5432/postgres?user=username&password=secret", jdbcUrl)
    }

    @Test
    fun `default jdbc url - google factory`() {
        val fakeEnv = mapOf(
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "postgres",
            "DB_USERNAME" to "username",
            "DB_PASSWORD" to "secret",
        )
        val jdbcUrl = jdbcUrlWithGoogleSocketFactory("dbinstance", ConnectionConfigFactory.Env(fakeEnv), gcpTeamProjectId = "project_id")
        assertEquals("jdbc:postgresql://localhost:5432/postgres?user=username&password=secret&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=project_id:europe-north1:dbinstance", jdbcUrl)
    }

    @Test
    fun `default jdbc url - google factory - with jdbc_url set`() {
        val fakeEnv = mapOf(
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432",
            "DB_DATABASE" to "postgres",
            "DB_USERNAME" to "username",
            "DB_PASSWORD" to "secret",
            "DB_JDBC_URL" to "jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar",
        )
        val jdbcUrl = jdbcUrlWithGoogleSocketFactory("dbinstance", ConnectionConfigFactory.Env(fakeEnv), gcpTeamProjectId = "project_id")
        assertEquals("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar", jdbcUrl)
    }
}