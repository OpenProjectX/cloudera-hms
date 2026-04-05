package org.openprojectx.cloudera.hms.core

import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

object HiveSchemaInitializer {
    fun initializeIfNeeded(config: ClouderaHiveMetastoreConfig) {
        if (!config.initializeSchema) {
            return
        }

        DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword).use { connection ->
            if (schemaAlreadyExists(connection)) {
                return
            }

            connection.autoCommit = false
            val statements = splitSqlStatements(resolveSchemaSql(config))
            try {
                connection.createStatement().use { statement ->
                    statements.forEach(statement::execute)
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw IllegalStateException("Failed to initialize Hive metastore schema", ex)
            }
        }
    }

    private fun schemaAlreadyExists(connection: Connection): Boolean =
        connection.prepareStatement(
            """
            select 1
            from information_schema.tables
            where table_schema = current_schema()
              and table_name = 'VERSION'
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { resultSet -> resultSet.next() }
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
