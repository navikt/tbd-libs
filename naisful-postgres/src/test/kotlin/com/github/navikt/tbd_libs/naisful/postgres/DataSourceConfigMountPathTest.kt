package com.github.navikt.tbd_libs.naisful.postgres

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class DataSourceConfigMountPathTest {

    @Test
    fun `default jdbc url - env`(@TempDir tempDir: Path) {
        tempDir.resolve("DB_HOST").writeText("localhost")
        tempDir.resolve("DB_PORT").writeText("5432")
        tempDir.resolve("DB_DATABASE").writeText("postgres")
        tempDir.resolve("DB_USERNAME").writeText("username")
        tempDir.resolve("DB_PASSWORD").writeText("secret")
        val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.MountPath(tempDir.toString()))
        assertEquals("jdbc:postgresql://localhost:5432/postgres?user=username&password=secret", jdbcUrl)
    }

    @Test
    fun `default jdbc url - jdbc_url set`(@TempDir tempDir: Path) {
        tempDir.resolve("DB_HOST").writeText("localhost")
        tempDir.resolve("DB_PORT").writeText("5432")
        tempDir.resolve("DB_DATABASE").writeText("postgres")
        tempDir.resolve("DB_USERNAME").writeText("username")
        tempDir.resolve("DB_PASSWORD").writeText("secret")
        tempDir.resolve("DB_JDBC_URL").writeText("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar")
        val jdbcUrl = defaultJdbcUrl(ConnectionConfigFactory.MountPath(tempDir.toString()))
        assertEquals("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar", jdbcUrl)
    }

    @Test
    fun `default jdbc url - google factory`(@TempDir tempDir: Path) {
        tempDir.resolve("DB_HOST").writeText("localhost")
        tempDir.resolve("DB_PORT").writeText("5432")
        tempDir.resolve("DB_DATABASE").writeText("postgres")
        tempDir.resolve("DB_USERNAME").writeText("username")
        tempDir.resolve("DB_PASSWORD").writeText("secret")
        val jdbcUrl = jdbcUrlWithGoogleSocketFactory("dbinstance", ConnectionConfigFactory.MountPath(tempDir.toString()), gcpTeamProjectId = "project_id")
        assertEquals("jdbc:postgresql://localhost:5432/postgres?user=username&password=secret&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=project_id:europe-north1:dbinstance", jdbcUrl)
    }

    @Test
    fun `default jdbc url - google factory - with jdbc_url set`(@TempDir tempDir: Path) {
        tempDir.resolve("DB_HOST").writeText("localhost")
        tempDir.resolve("DB_PORT").writeText("5432")
        tempDir.resolve("DB_DATABASE").writeText("postgres")
        tempDir.resolve("DB_USERNAME").writeText("username")
        tempDir.resolve("DB_PASSWORD").writeText("secret")
        tempDir.resolve("DB_JDBC_URL").writeText("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar")
        val jdbcUrl = jdbcUrlWithGoogleSocketFactory("dbinstance", ConnectionConfigFactory.MountPath(tempDir.toString()), gcpTeamProjectId = "project_id")
        assertEquals("jdbc:postgresql://remote_ip:5432/testdb?user=foo&password=bar&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=project_id:europe-north1:dbinstance", jdbcUrl)
    }
}