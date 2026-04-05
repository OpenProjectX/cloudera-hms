package org.openprojectx.cloudera.hms.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreConfig
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreProcess
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.nio.file.Files

class ClouderaHiveMetastoreExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)
        if (store.get(STATE_KEY) != null) {
            return
        }

        val postgres = PostgreSQLContainer(
            DockerImageName.parse("postgres:14")
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            withDatabaseName("metastore_db")
            withUsername("hive")
            withPassword("hive-password")
            start()
        }

        val warehouseDir = Files.createTempDirectory("cloudera-hms-warehouse-")
        val metastore = ClouderaHiveMetastoreProcess.start(
            ClouderaHiveMetastoreConfig(
                port = freePort(),
                warehouseDir = warehouseDir,
                jdbcUrl = postgres.jdbcUrl,
                jdbcUser = postgres.username,
                jdbcPassword = postgres.password,
            )
        )

        store.put(STATE_KEY, State(postgres, metastore))
    }

    override fun afterAll(context: ExtensionContext) {
        val state = store(context).remove(STATE_KEY, State::class.java) ?: return
        state.metastore.close()
        state.postgres.stop()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == ClouderaHiveMetastoreProcess::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        requireNotNull(store(extensionContext).get(STATE_KEY, State::class.java)).metastore

    private fun store(context: ExtensionContext): ExtensionContext.Store =
        context.getStore(ExtensionContext.Namespace.create(javaClass, context.requiredTestClass))

    private data class State(
        val postgres: PostgreSQLContainer,
        val metastore: ClouderaHiveMetastoreProcess,
    )

    companion object {
        private const val STATE_KEY = "cloudera-hms-state"

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
