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

    companion object {
        fun from(value: String?): MetastoreDatabaseType =
            when (value?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "postgres", "postgresql" -> POSTGRESQL
                "mariadb", "mysql" -> MARIADB
                else -> error("Unsupported metastore database type: $value")
            }
    }
}
