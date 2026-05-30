package org.openprojectx.cloudera.hms.spark

import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreConfig
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreProcess
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

class SparkIcebergGcsTckTest {
    @Test
    fun `spark can use iceberg tables backed by gcs through fake gcs server and cloudera hms`() {
        val hostPort = freePort()
        val fakeGcs = FixedHostPortContainer(DockerImageName.parse("fsouza/fake-gcs-server:1.54"), hostPort)
            .withCommand(
                "-scheme",
                "http",
                "-backend",
                "memory",
                "-external-url",
                "http://localhost:$hostPort",
            )
            .withExposedPorts(FAKE_GCS_PORT)
            .waitingFor(
                Wait.forHttp("/storage/v1/b")
                    .forPort(FAKE_GCS_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )

        fakeGcs.start()

        val endpoint = "http://${fakeGcs.host}:${fakeGcs.getMappedPort(FAKE_GCS_PORT)}"
        val bucket = "iceberg-${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val catalog = "iceberg_catalog"
        val namespace = "ns_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val table = "events"
        val renamedTable = "events_v2"
        val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
            withDatabaseName("metastore_db")
            withUsername("hive")
            withPassword("hive-password")
            start()
        }
        val metastore = ClouderaHiveMetastoreProcess.start(
            ClouderaHiveMetastoreConfig(
                port = freePort(),
                warehouseDir = Files.createTempDirectory("cloudera-hms-warehouse-"),
                jdbcUrl = postgres.jdbcUrl,
                jdbcUser = postgres.username,
                jdbcPassword = postgres.password,
                logLevel = "INFO",
                extraConfiguration = gcsHadoopConfiguration(endpoint)
            )
        )

        createBucket(endpoint, bucket)
        assertTrue(listBuckets(endpoint).contains(bucket))

        val spark = SparkSession.builder()
            .appName("cloudera-hms-iceberg-gcs-tck")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.shuffle.partitions", "1")
            .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config("spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.$catalog.type", "hive")
            .config("spark.sql.catalog.$catalog.uri", metastore.config.thriftUri)
            .config("spark.sql.catalog.$catalog.warehouse", "gs://$bucket/warehouse")
            .config("spark.sql.catalog.$catalog.io-impl", "org.apache.iceberg.gcp.gcs.GCSFileIO")
            .config("spark.sql.catalog.$catalog.gcs.service.host", endpoint)
            .config("spark.sql.catalog.$catalog.gcs.no-auth", "true")
            .config("spark.sql.catalog.$catalog.gcs.project-id", PROJECT_ID)
            .config("spark.hadoop.hive.metastore.uris", metastore.config.thriftUri)
            .config("hive.metastore.uris", metastore.config.thriftUri)
            .applyGcsHadoopConfiguration(endpoint)
            .getOrCreate()

        try {
            spark.sql("CREATE NAMESPACE $catalog.$namespace")
            spark.sql(
                """
                CREATE EXTERNAL TABLE $catalog.$namespace.$table (
                    id BIGINT,
                    category STRING,
                    dt STRING
                )
                USING iceberg
                PARTITIONED BY (dt)
                """.trimIndent()
            )

            spark.sql(
                """
                INSERT INTO $catalog.$namespace.$table VALUES
                (1, 'alpha', '2026-04-05'),
                (2, 'beta', '2026-04-05'),
                (3, 'gamma', '2026-04-06')
                """.trimIndent()
            )

            val countBeforeRename = spark.sql("SELECT COUNT(*) FROM $catalog.$namespace.$table").collectAsList()[0].getLong(0)
            assertEquals(3L, countBeforeRename)

            spark.sql("ALTER TABLE $catalog.$namespace.$table RENAME TO $catalog.$namespace.$renamedTable")

            val groupedCounts = spark.sql(
                """
                SELECT dt, COUNT(*) AS cnt
                FROM $catalog.$namespace.$renamedTable
                GROUP BY dt
                ORDER BY dt
                """.trimIndent()
            ).collectAsList()

            assertEquals(2L, groupedCounts[0].getLong(1))
            assertEquals(1L, groupedCounts[1].getLong(1))
            assertTrue(
                spark.sql("SHOW TABLES IN $catalog.$namespace")
                    .collectAsList()
                    .any { it.getString(1) == renamedTable }
            )
            assertTrue(listObjectNames(endpoint, bucket).any { it.startsWith("warehouse/$namespace.db/$table/") })
        } finally {
            try {
                spark.sql("DROP TABLE IF EXISTS $catalog.$namespace.$renamedTable")
            } finally {
                spark.stop()
                metastore.close()
                postgres.stop()
                fakeGcs.stop()
            }
        }
    }

    private fun SparkSession.Builder.applyGcsHadoopConfiguration(endpoint: String): SparkSession.Builder =
        gcsHadoopConfiguration(endpoint).entries.fold(this) { builder, (key, value) ->
            builder.config("spark.hadoop.$key", value)
        }

    private fun createBucket(endpoint: String, bucket: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint/storage/v1/b?project=$PROJECT_ID"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"name":"$bucket"}"""))
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())

        require(response.statusCode() in 200..299) {
            "Failed to create fake GCS bucket $bucket: HTTP ${response.statusCode()} ${response.body()}"
        }
    }

    private fun listBuckets(endpoint: String): List<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint/storage/v1/b"))
            .GET()
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())

        require(response.statusCode() in 200..299) {
            "Failed to list fake GCS buckets: HTTP ${response.statusCode()} ${response.body()}"
        }

        return NAME_REGEX.findAll(response.body())
            .map { it.groupValues[1] }
            .toList()
    }

    private fun listObjectNames(endpoint: String, bucket: String): List<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint/storage/v1/b/$bucket/o"))
            .GET()
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())

        require(response.statusCode() in 200..299) {
            "Failed to list fake GCS bucket $bucket: HTTP ${response.statusCode()} ${response.body()}"
        }

        return NAME_REGEX.findAll(response.body())
            .map { it.groupValues[1] }
            .toList()
    }

    private class FixedHostPortContainer(
        imageName: DockerImageName,
        private val hostPort: Int,
    ) : GenericContainer<FixedHostPortContainer>(imageName) {
        override fun configure() {
            addFixedExposedPort(hostPort, FAKE_GCS_PORT)
        }
    }

    companion object {
        private const val FAKE_GCS_PORT = 4443
        private const val PROJECT_ID = "test-project"
        private val NAME_REGEX = """"name"\s*:\s*"([^"]+)"""".toRegex()

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun gcsHadoopConfiguration(endpoint: String): Map<String, String> =
            mapOf(
                "fs.gs.impl" to "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem",
                "fs.AbstractFileSystem.gs.impl" to "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS",
                "fs.gs.project.id" to PROJECT_ID,
                "fs.gs.auth.type" to "UNAUTHENTICATED",
                "google.cloud.auth.type" to "UNAUTHENTICATED",
                "fs.gs.auth.service.account.enable" to "false",
                "google.cloud.auth.service.account.enable" to "false",
                "fs.gs.storage.root.url" to "$endpoint/",
                "fs.gs.storage.service.path" to "/storage/v1/",
                "fs.gs.client.type" to "HTTP_API_CLIENT",
                "fs.gs.grpc.enable" to "false",
                "fs.gs.grpc.write.enable" to "false",
                "fs.gs.create.items.conflict.check.enable" to "false",
                "fs.gs.performance.cache.enable" to "false",
                "fs.gs.max.requests.per.batch" to "1",
                "fs.gs.outputstream.direct.upload.enable" to "true",
                "fs.gs.client.upload.type" to "WRITE_TO_DISK_THEN_UPLOAD",
            )
    }
}
