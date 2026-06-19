package org.openprojectx.cloudera.hms.core

import java.util.Locale

enum class MetastoreDatabaseType(
    val defaultJdbcDriver: String,
    val defaultSchemaResource: String,
) {
    POSTGRESQL(
        defaultJdbcDriver = "org.postgresql.Driver",
        defaultSchemaResource = "/hive-schema-3.1.3000.postgres.sql",
    ),
    MARIADB(
        defaultJdbcDriver = "org.mariadb.jdbc.Driver",
        defaultSchemaResource = "/hive-schema-3.1.3000.mysql.sql",
    );

    fun compatibleJdbcUrl(jdbcUrl: String): String =
        when (this) {
            POSTGRESQL -> jdbcUrl
            MARIADB -> withJdbcParameter(jdbcUrl, "useMysqlMetadata", "true")
        }

    companion object {
        fun from(value: String?): MetastoreDatabaseType =
            when (value?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "postgres", "postgresql" -> POSTGRESQL
                "mariadb", "mysql" -> MARIADB
                else -> error("Unsupported metastore database type: $value")
            }

        private fun withJdbcParameter(jdbcUrl: String, name: String, value: String): String {
            if (Regex("([?&])${Regex.escape(name)}=", RegexOption.IGNORE_CASE).containsMatchIn(jdbcUrl)) {
                return jdbcUrl
            }

            val fragmentIndex = jdbcUrl.indexOf('#')
            val base = if (fragmentIndex >= 0) jdbcUrl.substring(0, fragmentIndex) else jdbcUrl
            val fragment = if (fragmentIndex >= 0) jdbcUrl.substring(fragmentIndex) else ""
            val separator = if (base.contains('?')) "&" else "?"
            return "$base$separator$name=$value$fragment"
        }
    }
}
