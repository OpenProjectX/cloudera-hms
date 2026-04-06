package org.openprojectx.cloudera.hms.tck

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.TableType
import org.apache.hadoop.hive.metastore.api.Database
import org.apache.hadoop.hive.metastore.api.FieldSchema
import org.apache.hadoop.hive.metastore.api.Partition
import org.apache.hadoop.hive.metastore.api.SerDeInfo
import org.apache.hadoop.hive.metastore.api.StorageDescriptor
import org.apache.hadoop.hive.metastore.api.Table
import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.use

abstract class AbstractHiveMetastoreClientTck {
    @Test
    fun `supports Hive metastore lifecycle operations`() {
        postgresContainer().use { postgres ->
            postgres.start()

            val warehouseDir = Files.createTempDirectory("cloudera-hms-tck-warehouse-")
            classpathMetastore(
                HmsTckConfig(
                    port = freePort(),
                    warehouseDir = warehouseDir,
                    jdbcUrl = postgres.jdbcUrl,
                    jdbcUser = postgres.username,
                    jdbcPassword = postgres.password,
                    schemaResource = schemaSqlPath(),
                    logLevel = logLevel(),
                )
            ).use { metastore ->
                val databaseName = "db_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                val tableName = "events"
                val renamedTableName = "events_archive"

                metastore.createClient().use { client ->
                    client.createDatabase(
                        Database().apply {
                            name = databaseName
                            locationUri = metastore.config.warehouseDir.resolve(databaseName).toUri().toString()
                        }
                    )

                    client.createTable(
                        Table().apply {
                            setDbName(databaseName)
                            setTableName(tableName)
                            setOwner("integration-test")
                            setTableType(TableType.MANAGED_TABLE.name)
                            setPartitionKeys(listOf(FieldSchema("dt", "string", null)))
                            setSd(StorageDescriptor().apply {
                                setCols(
                                    listOf(
                                        FieldSchema("event_id", "string", null),
                                        FieldSchema("user_id", "bigint", null),
                                    )
                                )
                                setInputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat")
                                setOutputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat")
                                setSerdeInfo(
                                    SerDeInfo().apply {
                                        setSerializationLib("org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe")
                                    }
                                )
                                setLocation(
                                    metastore.config.warehouseDir.resolve("$databaseName.db/$tableName").toUri().toString()
                                )
                            })
                        }
                    )

                    client.add_partition(
                        Partition().apply {
                            setDbName(databaseName)
                            setTableName(tableName)
                            setValues(listOf("2026-04-05"))
                            setSd(
                                client.getTable(databaseName, tableName).sd.deepCopy().apply {
                                    setLocation(
                                        metastore.config.warehouseDir
                                            .resolve("$databaseName.db/$tableName/dt=2026-04-05")
                                            .toUri()
                                            .toString()
                                    )
                                }
                            )
                        }
                    )

                    val loadedPartition = client.getPartition(databaseName, tableName, "dt=2026-04-05")
                    assertEquals(listOf("2026-04-05"), loadedPartition.values)
                    assertEquals(listOf("events"), client.getAllTables(databaseName))

                    val table = client.getTable(databaseName, tableName)
                    table.setTableName(renamedTableName)
                    client.alter_table(databaseName, tableName, table)

                    assertTrue(client.tableExists(databaseName, renamedTableName))
                    assertFalse(client.tableExists(databaseName, tableName))
                    assertEquals(listOf("dt=2026-04-05"), client.listPartitionNames(databaseName, renamedTableName, 10))

                    client.dropTable(databaseName, renamedTableName)
                    client.dropDatabase(databaseName, true, true, true)
                }
            }
        }
    }

    protected open fun postgresImage(): String = "postgres:14"

    protected open fun schemaSqlPath(): String = "/hive-schema-3.1.3000.postgres.sql"

    protected open fun logLevel(): String = "INFO"

    protected open fun classpathMetastore(config: HmsTckConfig): ClasspathHiveMetastoreProcess =
        ClasspathHiveMetastoreProcess.start(config)

    private fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer(
            DockerImageName.parse(postgresImage())
                .asCompatibleSubstituteFor("postgres")
        ).apply {
            withDatabaseName("metastore_db")
            withUsername("hive")
            withPassword("hive-password")
        }

    companion object {
        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

data class HmsTckConfig(
    val host: String = "127.0.0.1",
    val port: Int,
    val warehouseDir: Path,
    val jdbcUrl: String,
    val jdbcDriver: String = "org.postgresql.Driver",
    val jdbcUser: String,
    val jdbcPassword: String,
    val schemaResource: String = "/hive-schema-3.1.3000.postgres.sql",
    val schemaFile: Path? = null,
    val initializeSchema: Boolean = true,
    val startupTimeoutMillis: Long = 120_000,
    val extraConfiguration: Map<String, String> = emptyMap(),
    val logLevel: String = "INFO",
    val logConfigFile: Path? = null,
) {
    val thriftUri: String
        get() = "thrift://$host:$port"
}

class ClasspathHiveMetastoreProcess private constructor(
    val config: HmsTckConfig,
    private val process: Process,
) : AutoCloseable {
    fun createClient(): HiveMetaStoreClient = HiveMetaStoreClient(newClientConfiguration(config))

    override fun close() {
        if (!process.isAlive) {
            return
        }

        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
        }
    }

    companion object {
        fun start(config: HmsTckConfig): ClasspathHiveMetastoreProcess {
            Files.createDirectories(config.warehouseDir)
            val extraConfigFile = extraConfigFile(config)
            val logConfigFile = logConfigFile(config)

            val command = buildList {
                add(javaBinary())
                add("-Dcloudera.hms.host=${config.host}")
                add("-Dcloudera.hms.port=${config.port}")
                add("-Dcloudera.hms.jdbc.url=${config.jdbcUrl}")
                add("-Dcloudera.hms.jdbc.driver=${config.jdbcDriver}")
                add("-Dcloudera.hms.jdbc.user=${config.jdbcUser}")
                add("-Dcloudera.hms.jdbc.password=${config.jdbcPassword}")
                add("-Dcloudera.hms.warehouse.dir=${config.warehouseDir}")
                add("-Dcloudera.hms.initialize-schema=${config.initializeSchema}")
                add("-Dcloudera.hms.schema.resource=${config.schemaResource}")
                config.schemaFile?.let { add("-Dcloudera.hms.schema.file=$it") }
                extraConfigFile?.let { add("-Dcloudera.hms.extra-config-file=$it") }
                add("-Dcloudera.hms.log.level=${config.logLevel}")
                add("-Dlog4j.configurationFile=$logConfigFile")
                add("-Dlog4j2.configurationFile=$logConfigFile")
                add("-cp")
                add(System.getProperty("java.class.path"))
                add("org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            streamLogs(process)
            waitUntilReady(config, process)
            return ClasspathHiveMetastoreProcess(config, process)
        }

        private fun newClientConfiguration(config: HmsTckConfig): Configuration =
            MetastoreConf.newMetastoreConf().apply {
                MetastoreConf.setVar(this, MetastoreConf.ConfVars.THRIFT_URIS, config.thriftUri)
                MetastoreConf.setTimeVar(this, MetastoreConf.ConfVars.CLIENT_SOCKET_TIMEOUT, 30, TimeUnit.SECONDS)
                set("hive.metastore.uris", config.thriftUri)
                set("metastore.thrift.uris", config.thriftUri)
                set("fs.defaultFS", "file:///")
            }

        private fun waitUntilReady(config: HmsTckConfig, process: Process) {
            val deadline = Instant.now().plusMillis(config.startupTimeoutMillis)
            var lastFailure: Exception? = null

            while (Instant.now().isBefore(deadline)) {
                if (!process.isAlive) {
                    throw IllegalStateException("Hive metastore process exited early with code ${process.exitValue()}")
                }

                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(config.host, config.port), 1_000)
                    }
                    HiveMetaStoreClient(newClientConfiguration(config)).use { }
                    return
                } catch (ex: Exception) {
                    lastFailure = ex
                    Thread.sleep(1_000)
                }
            }

            throw IllegalStateException(
                "Hive metastore did not become ready within ${Duration.ofMillis(config.startupTimeoutMillis)}",
                lastFailure
            )
        }

        private fun streamLogs(process: Process) {
            val thread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line -> System.err.println("[cloudera-hms] $line") }
                }
            }
            thread.name = "cloudera-hms-log-forwarder"
            thread.isDaemon = true
            thread.start()
        }

        private fun javaBinary(): String {
            val javaHome = System.getProperty("java.home")
            val candidate = javaHome?.let { Path.of(it, "bin", "java") }
            if (candidate != null && Files.isExecutable(candidate)) {
                return candidate.toString()
            }
            return "java"
        }

        private fun extraConfigFile(config: HmsTckConfig): Path? {
            if (config.extraConfiguration.isEmpty()) {
                return null
            }

            val file = Files.createTempFile("cloudera-hms-tck-extra-", ".properties")
            val properties = Properties()
            config.extraConfiguration.toSortedMap().forEach { (key, value) -> properties.setProperty(key, value) }
            file.toFile().outputStream().use { properties.store(it, "cloudera-hms tck extra configuration") }
            return file
        }

        private fun logConfigFile(config: HmsTckConfig): Path {
            config.logConfigFile?.let { return it }

            val file = Files.createTempFile("cloudera-hms-tck-log4j2-", ".properties")
            file.writeText(
                """
                status = WARN
                name = ClouderaHmsTckLogConfig
                appender.console.type = Console
                appender.console.name = CONSOLE
                appender.console.layout.type = PatternLayout
                appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c{1.} - %m%n
                rootLogger.level = ${config.logLevel.uppercase(Locale.ROOT)}
                rootLogger.appenderRefs = console
                rootLogger.appenderRef.console.ref = CONSOLE
                """.trimIndent()
            )
            return file
        }
    }
}
