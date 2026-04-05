package org.openprojectx.cloudera.hms.core

import java.nio.file.Path
import java.nio.file.Paths

data class ClouderaHiveMetastoreConfig(
    val host: String = "127.0.0.1",
    val port: Int = 9083,
    val warehouseDir: Path = Paths.get("build/hms/warehouse").toAbsolutePath().normalize(),
    val jdbcUrl: String,
    val jdbcDriver: String = "org.postgresql.Driver",
    val jdbcUser: String,
    val jdbcPassword: String,
    val schemaResource: String = "/hive-schema-3.1.3000.postgres.sql",
    val schemaFile: Path? = null,
    val initializeSchema: Boolean = true,
    val startupTimeoutMillis: Long = 120_000,
) {
    init {
        require(port in 1..65535) { "port must be between 1 and 65535" }
    }

    val thriftUri: String
        get() = "thrift://$host:$port"
}
