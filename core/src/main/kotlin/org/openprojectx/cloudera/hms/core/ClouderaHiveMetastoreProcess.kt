package org.openprojectx.cloudera.hms.core

import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.Properties
import java.util.concurrent.TimeUnit

class ClouderaHiveMetastoreProcess private constructor(
    val config: ClouderaHiveMetastoreConfig,
    private val process: Process,
) : AutoCloseable {
    fun createClient(): HiveMetaStoreClient = HiveMetaStoreClient(HiveMetastoreConfigurations.newClientConfiguration(config))

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
        fun start(config: ClouderaHiveMetastoreConfig): ClouderaHiveMetastoreProcess {
            Files.createDirectories(config.warehouseDir)
            val extraConfigFile = extraConfigFile(config)
            val logConfigFile = logConfigFile(config)

            val javaBinary = javaBinary()
            val classpath = System.getProperty("java.class.path")
            val command = buildList {
                add(javaBinary)
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
                add(classpath)
                add("org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            streamLogs(process)
            waitUntilReady(config, process)
            return ClouderaHiveMetastoreProcess(config, process)
        }

        private fun waitUntilReady(config: ClouderaHiveMetastoreConfig, process: Process) {
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
                    HiveMetaStoreClient(HiveMetastoreConfigurations.newClientConfiguration(config)).use { }
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
            val candidate = javaHome?.let { java.nio.file.Path.of(it, "bin", "java") }
            if (candidate != null && Files.isExecutable(candidate)) {
                return candidate.toString()
            }
            return "java"
        }

        private fun extraConfigFile(config: ClouderaHiveMetastoreConfig): Path? {
            if (config.extraConfiguration.isEmpty()) {
                return null
            }

            val file = Files.createTempFile("cloudera-hms-extra-", ".properties")
            val properties = Properties()
            config.extraConfiguration.toSortedMap().forEach { (key, value) -> properties.setProperty(key, value) }
            file.toFile().outputStream().use { properties.store(it, "cloudera-hms extra configuration") }
            return file
        }

        private fun logConfigFile(config: ClouderaHiveMetastoreConfig): Path {
            config.logConfigFile?.let { return it }

            val level = config.logLevel.uppercase(Locale.ROOT)
            val template = requireNotNull(
                ClouderaHiveMetastoreProcess::class.java.getResource("/hive-log4j2.properties.template")
            ) {
                "Missing bundled hive-log4j2 template"
            }.readText()

            val file = Files.createTempFile("cloudera-hms-log4j2-", ".properties")
            Files.writeString(file, template.replace("\${ROOT_LOG_LEVEL}", level))
            return file
        }
    }
}
