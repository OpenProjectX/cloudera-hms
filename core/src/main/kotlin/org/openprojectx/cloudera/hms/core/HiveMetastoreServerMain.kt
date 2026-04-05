package org.openprojectx.cloudera.hms.core

import org.apache.hadoop.hive.metastore.HiveMetaStore
import org.apache.hadoop.hive.metastore.security.HadoopThriftAuthBridge
import java.nio.file.Path
import java.util.Properties

fun main() {
    val config = ClouderaHiveMetastoreConfig(
        host = requiredProperty("cloudera.hms.host"),
        port = requiredProperty("cloudera.hms.port").toInt(),
        warehouseDir = Path.of(requiredProperty("cloudera.hms.warehouse.dir")),
        jdbcUrl = requiredProperty("cloudera.hms.jdbc.url"),
        jdbcDriver = System.getProperty("cloudera.hms.jdbc.driver", "org.postgresql.Driver"),
        jdbcUser = requiredProperty("cloudera.hms.jdbc.user"),
        jdbcPassword = requiredProperty("cloudera.hms.jdbc.password"),
        schemaResource = System.getProperty("cloudera.hms.schema.resource", "/hive-schema-3.1.3000.postgres.sql"),
        schemaFile = System.getProperty("cloudera.hms.schema.file")?.let(Path::of),
        initializeSchema = System.getProperty("cloudera.hms.initialize-schema", "true").toBoolean(),
    )

    HiveSchemaInitializer.initializeIfNeeded(config)
    val conf = HiveMetastoreConfigurations.newServerConfiguration(config)
    System.getProperty("cloudera.hms.extra-config-file")?.let { extraConfig ->
        Path.of(extraConfig).toFile().inputStream().use { input ->
            Properties().apply { load(input) }.forEach { key, value ->
                conf.set(key.toString(), value.toString())
            }
        }
    }
    HiveMetaStore.startMetaStore(config.port, HadoopThriftAuthBridge.getBridge(), conf)
}

private fun requiredProperty(name: String): String =
    System.getProperty(name) ?: error("Missing required system property: $name")
