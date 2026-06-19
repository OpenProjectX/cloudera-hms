package org.openprojectx.cloudera.hms.core

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

object HiveSchemaInitializer {
    fun initializeIfNeeded(config: ClouderaHiveMetastoreConfig) {
        if (!config.initializeSchema) {
            return
        }

        DriverManager.getConnection(config.effectiveJdbcUrl, config.jdbcUser, config.jdbcPassword).use { connection ->
            if (schemaAlreadyExists(connection, config.databaseType)) {
                return
            }

            connection.autoCommit = false
            val statements = splitSqlStatements(resolveSchemaSql(config))
            try {
                connection.createStatement().use { statement ->
                    configureBeforeSchemaInitialization(config.databaseType, statement)
                    statements.forEach(statement::execute)
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw IllegalStateException("Failed to initialize Hive metastore schema", ex)
            } finally {
                connection.createStatement().use { statement ->
                    restoreAfterSchemaInitialization(config.databaseType, statement)
                }
            }
        }
    }

    private fun configureBeforeSchemaInitialization(
        databaseType: MetastoreDatabaseType,
        statement: java.sql.Statement,
    ) {
        when (databaseType) {
            MetastoreDatabaseType.POSTGRESQL -> Unit
            MetastoreDatabaseType.MARIADB -> {
                statement.execute("set @cloudera_hms_old_foreign_key_checks=@@FOREIGN_KEY_CHECKS")
                statement.execute("set FOREIGN_KEY_CHECKS=0")
            }
        }
    }

    private fun restoreAfterSchemaInitialization(
        databaseType: MetastoreDatabaseType,
        statement: java.sql.Statement,
    ) {
        when (databaseType) {
            MetastoreDatabaseType.POSTGRESQL -> Unit
            MetastoreDatabaseType.MARIADB ->
                statement.execute("set FOREIGN_KEY_CHECKS=coalesce(@cloudera_hms_old_foreign_key_checks, 1)")
        }
    }

    private fun schemaAlreadyExists(connection: Connection, databaseType: MetastoreDatabaseType): Boolean {
        val sql = when (databaseType) {
            MetastoreDatabaseType.POSTGRESQL ->
                """
                select 1
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'VERSION'
                """.trimIndent()

            MetastoreDatabaseType.MARIADB ->
                """
                select 1
                from information_schema.tables
                where table_schema = database()
                  and table_name = 'VERSION'
                """.trimIndent()
        }

        return connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { resultSet -> resultSet.next() }
        }
    }

    private fun resolveSchemaSql(config: ClouderaHiveMetastoreConfig): String {
        config.schemaFile?.let { file ->
            return Files.readString(file)
        }

        return requireNotNull(HiveSchemaInitializer::class.java.getResource(config.schemaResource)) {
            "Schema resource not found: ${config.schemaResource}"
        }.readText()
    }

    internal fun splitSqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inLineComment = false
        var inBlockComment = false
        var index = 0

        while (index < sql.length) {
            val char = sql[index]
            val next = sql.getOrNull(index + 1)

            when {
                inLineComment -> {
                    if (char == '\n') {
                        inLineComment = false
                        current.append(char)
                    }
                }

                inBlockComment -> {
                    if (char == '*' && next == '/') {
                        inBlockComment = false
                        index++
                    }
                }

                !inSingleQuote && !inDoubleQuote && char == '-' && next == '-' -> {
                    inLineComment = true
                    index++
                }

                !inSingleQuote && !inDoubleQuote && char == '/' && next == '*' -> {
                    inBlockComment = true
                    index++
                }

                char == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(char)
                }

                char == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }

                char == ';' && !inSingleQuote && !inDoubleQuote -> {
                    val statement = current.toString().trim()
                    if (statement.isNotEmpty()) {
                        statements += statement
                    }
                    current.setLength(0)
                }

                else -> current.append(char)
            }

            index++
        }

        val trailing = current.toString().trim()
        if (trailing.isNotEmpty()) {
            statements += trailing
        }

        return statements
    }
}
