package org.openprojectx.cloudera.hms.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreConfig
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreProcess
import org.openprojectx.cloudera.hms.core.MetastoreDatabaseType
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ClouderaHiveMetastoreExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)
        if (store.get(STATE_KEY) != null) {
            return
        }

        val settings = settings(context)
        val database = databaseContainer(settings).apply { container.start() }

        val warehouseDir = Files.createTempDirectory("cloudera-hms-warehouse-")
        val metastore = ClouderaHiveMetastoreProcess.start(
            ClouderaHiveMetastoreConfig(
                port = freePort(),
                warehouseDir = warehouseDir,
                jdbcUrl = database.jdbcUrl(),
                databaseType = settings.databaseType,
                jdbcUser = database.username,
                jdbcPassword = database.password,
                schemaResource = settings.schemaResource ?: settings.databaseType.defaultSchemaResource,
                schemaFile = settings.schemaFile,
                logLevel = settings.logLevel,
            )
        )

        store.put(STATE_KEY, State(database.container, metastore))
    }

    override fun afterAll(context: ExtensionContext) {
        val state = store(context).remove(STATE_KEY, State::class.java) ?: return
        state.metastore.close()
        state.database.stop()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == ClouderaHiveMetastoreProcess::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        requireNotNull(store(extensionContext).get(STATE_KEY, State::class.java)).metastore

    private fun store(context: ExtensionContext): ExtensionContext.Store =
        context.getStore(ExtensionContext.Namespace.create(javaClass, context.requiredTestClass))

    private fun settings(context: ExtensionContext): Settings {
        val annotation = context.requiredTestClass.getAnnotation(ClouderaHiveMetastoreTest::class.java)
            ?: error("${context.requiredTestClass.name} must be annotated with @ClouderaHiveMetastoreTest")

        val schemaPath = annotation.schemaSqlPath.trim()
        val schemaFile = schemaFile(schemaPath)
        val schemaResource = schemaResource(schemaPath, schemaFile)
        return Settings(
            databaseType = MetastoreDatabaseType.from(annotation.databaseType),
            postgresImage = annotation.postgresImage,
            mariadbImage = annotation.mariadbImage,
            schemaFile = schemaFile,
            schemaResource = schemaResource,
            logLevel = annotation.logLevel,
        )
    }

    private fun databaseContainer(settings: Settings): DatabaseContainer =
        when (settings.databaseType) {
            MetastoreDatabaseType.POSTGRESQL -> PostgreSQLContainer(
                DockerImageName.parse(settings.postgresImage)
                    .asCompatibleSubstituteFor("postgres")
            ).apply {
                withDatabaseName(DATABASE_NAME)
                withUsername(DATABASE_USER)
                withPassword(DATABASE_PASSWORD)
            }.let {
                DatabaseContainer(
                    container = it,
                    jdbcUrl = { it.jdbcUrl },
                    username = it.username,
                    password = it.password,
                )
            }

            MetastoreDatabaseType.MARIADB -> GenericContainer(
                DockerImageName.parse(settings.mariadbImage)
                    .asCompatibleSubstituteFor("mariadb")
            ).apply {
                withExposedPorts(MARIADB_PORT)
                withEnv("MARIADB_DATABASE", DATABASE_NAME)
                withEnv("MARIADB_USER", DATABASE_USER)
                withEnv("MARIADB_PASSWORD", DATABASE_PASSWORD)
                withEnv("MARIADB_RANDOM_ROOT_PASSWORD", "yes")
                waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
            }.let {
                DatabaseContainer(
                    container = it,
                    jdbcUrl = { "jdbc:mariadb://${it.host}:${it.getMappedPort(MARIADB_PORT)}/$DATABASE_NAME" },
                    username = DATABASE_USER,
                    password = DATABASE_PASSWORD,
                )
            }
        }

    private fun schemaFile(schemaPath: String): Path? {
        if (schemaPath.isBlank()) {
            return null
        }

        val path = Path.of(schemaPath)
        return if (Files.exists(path)) path else null
    }

    private fun schemaResource(schemaPath: String, schemaFile: Path?): String? {
        if (schemaPath.isBlank() || schemaFile != null) {
            return null
        }

        val normalized = if (schemaPath.startsWith("/")) schemaPath else "/$schemaPath"
        val resourcePath = normalized.removePrefix("/")
        require(javaClass.getResource(normalized) != null || javaClass.classLoader.getResource(resourcePath) != null) {
            "Schema SQL path '$schemaPath' is neither an existing file nor a classpath resource"
        }
        return normalized
    }

    private data class State(
        val database: GenericContainer<*>,
        val metastore: ClouderaHiveMetastoreProcess,
    )

    private data class Settings(
        val databaseType: MetastoreDatabaseType,
        val postgresImage: String,
        val mariadbImage: String,
        val schemaFile: Path?,
        val schemaResource: String?,
        val logLevel: String,
    )

    private data class DatabaseContainer(
        val container: GenericContainer<*>,
        val jdbcUrl: () -> String,
        val username: String,
        val password: String,
    )

    companion object {
        private const val STATE_KEY = "cloudera-hms-state"
        private const val DATABASE_NAME = "metastore_db"
        private const val DATABASE_USER = "hive"
        private const val DATABASE_PASSWORD = "hive-password"
        private const val MARIADB_PORT = 3306

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
