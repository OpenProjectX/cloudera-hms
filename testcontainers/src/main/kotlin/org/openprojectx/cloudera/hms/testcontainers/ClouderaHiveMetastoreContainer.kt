package org.openprojectx.cloudera.hms.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Locale

class ClouderaHiveMetastoreContainer(
    dockerImageName: DockerImageName = imageName(),
) : GenericContainer<ClouderaHiveMetastoreContainer>(dockerImageName) {

    init {
        withExposedPorts(METASTORE_PORT)
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
    }

    fun withDatabaseName(databaseName: String): ClouderaHiveMetastoreContainer =
        apply { withEnv("POSTGRES_DB", databaseName) }

    fun withDatabaseUser(username: String): ClouderaHiveMetastoreContainer =
        apply {
            withEnv("POSTGRES_USER", username)
            withEnv("HMS_JDBC_USER", username)
        }

    fun withDatabasePassword(password: String): ClouderaHiveMetastoreContainer =
        apply {
            withEnv("POSTGRES_PASSWORD", password)
            withEnv("HMS_JDBC_PASSWORD", password)
        }

    fun withWarehouseDir(path: String): ClouderaHiveMetastoreContainer =
        apply { withEnv("HMS_WAREHOUSE_DIR", path) }

    fun withInitializeSchema(enabled: Boolean): ClouderaHiveMetastoreContainer =
        apply { withEnv("HMS_INITIALIZE_SCHEMA", enabled.toString()) }

    fun withSchemaResource(resourcePath: String): ClouderaHiveMetastoreContainer =
        apply { withEnv("HMS_SCHEMA_RESOURCE", resourcePath) }

    fun withLogLevel(level: String): ClouderaHiveMetastoreContainer =
        apply { withEnv("HMS_LOG_LEVEL", level) }

    fun withJdbcUrl(jdbcUrl: String): ClouderaHiveMetastoreContainer =
        apply { withEnv("HMS_JDBC_URL", jdbcUrl) }

    fun withExtraConfiguration(configuration: Map<String, String>): ClouderaHiveMetastoreContainer =
        apply {
            configuration.forEach { (key, value) ->
                withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
            }
        }

    fun thriftUri(): String = "thrift://$host:${getMappedPort(METASTORE_PORT)}"

    companion object {
        const val METASTORE_PORT: Int = 9083
        const val DEFAULT_IMAGE: String = "ghcr.io/openprojectx/cloudera-hms:latest"

        fun imageName(): DockerImageName {
            val configuredImage = System.getenv("CLOUDERA_HMS_TEST_IMAGE")
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_IMAGE
            return DockerImageName.parse(configuredImage)
        }

        private fun encodeConfigKey(key: String): String =
            key.lowercase(Locale.ROOT)
                .replace("-", "__")
                .replace(".", "_")
                .uppercase(Locale.ROOT)
    }
}
