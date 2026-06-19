package org.openprojectx.cloudera.hms.core

import java.nio.file.Path
import java.nio.file.Paths

data class ClouderaHiveMetastoreConfig(
    val host: String = "127.0.0.1",
    val port: Int = 9083,
    val warehouseDir: Path = Paths.get("build/hms/warehouse").toAbsolutePath().normalize(),
    val jdbcUrl: String,
    val databaseType: MetastoreDatabaseType = MetastoreDatabaseType.POSTGRESQL,
    val jdbcDriver: String = databaseType.defaultJdbcDriver,
    val jdbcUser: String,
    val jdbcPassword: String,
    val schemaResource: String = databaseType.defaultSchemaResource,
    val schemaFile: Path? = null,
    val initializeSchema: Boolean = true,
    val startupTimeoutMillis: Long = 120_000,
    val extraConfiguration: Map<String, String> = emptyMap(),
    val logLevel: String = "INFO",
    val logConfigFile: Path? = null,
) {
    init {
        require(port in 1..65535) { "port must be between 1 and 65535" }
    }

    val thriftUri: String
        get() = "thrift://$host:$port"

    val effectiveJdbcUrl: String
        get() = databaseType.compatibleJdbcUrl(jdbcUrl)
}
