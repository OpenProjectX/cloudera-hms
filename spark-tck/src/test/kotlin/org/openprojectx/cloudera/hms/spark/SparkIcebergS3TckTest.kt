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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.net.URI
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

class SparkIcebergS3TckTest {
    @Test
    fun `spark can use iceberg tables backed by s3 through localstack and cloudera hms`() {
        val localstack = GenericContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withEnv("SERVICES", "s3")
            .withEnv("AWS_DEFAULT_REGION", REGION)
            .withEnv("EAGER_SERVICE_LOADING", "1")
            .withExposedPorts(LOCALSTACK_PORT)
            .waitingFor(
                Wait.forHttp("/_localstack/health")
                    .forPort(LOCALSTACK_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )

        localstack.start()

        val endpoint = "http://${localstack.host}:${localstack.getMappedPort(LOCALSTACK_PORT)}"
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
                extraConfiguration = mapOf(
                    "fs.s3a.impl" to "org.apache.hadoop.fs.s3a.S3AFileSystem",
                    "fs.s3a.endpoint" to endpoint,
                    "fs.s3a.endpoint.region" to REGION,
                    "fs.s3a.path.style.access" to "true",
                    "fs.s3a.connection.ssl.enabled" to "false",
                    "fs.s3a.access.key" to ACCESS_KEY,
                    "fs.s3a.secret.key" to SECRET_KEY,
                    "fs.s3a.aws.credentials.provider" to "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider",
                )
            )
        )

        s3Client(endpoint).use { s3 ->
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
            assertTrue(s3.listBuckets().buckets().any { it.name() == bucket })
        }

        val spark = SparkSession.builder()
            .appName("cloudera-hms-iceberg-s3-tck")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.shuffle.partitions", "1")
            .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config("spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.$catalog.type", "hive")
            .config("spark.sql.catalog.$catalog.uri", metastore.config.thriftUri)
            .config("spark.sql.catalog.$catalog.warehouse", "s3a://$bucket/warehouse")
            .config("spark.hadoop.hive.metastore.uris", metastore.config.thriftUri)
            .config("hive.metastore.uris", metastore.config.thriftUri)
            .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
            .config("spark.hadoop.fs.s3a.endpoint", endpoint)
            .config("spark.hadoop.fs.s3a.endpoint.region", REGION)
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            .config("spark.hadoop.fs.s3a.access.key", ACCESS_KEY)
            .config("spark.hadoop.fs.s3a.secret.key", SECRET_KEY)
            .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
            .getOrCreate()

        try {
            spark.sql("CREATE NAMESPACE $catalog.$namespace")
            spark.sql(
                """
                CREATE TABLE $catalog.$namespace.$table (
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

            s3Client(endpoint).use { s3 ->
                val objects = s3.listObjectsV2 { request -> request.bucket(bucket).prefix("warehouse/") }.contents()
                assertTrue(objects.isNotEmpty())
            }
        } finally {
            try {
                spark.sql("DROP TABLE IF EXISTS $catalog.$namespace.$renamedTable")
//                spark.sql("DROP NAMESPACE IF EXISTS $catalog.$namespace")
            } finally {
                spark.stop()
                metastore.close()
                postgres.stop()
                localstack.stop()
            }
        }
    }

    private fun s3Client(endpoint: String): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(REGION))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
                )
            )
            .forcePathStyle(true)
            .build()

    companion object {
        private const val LOCALSTACK_PORT = 4566
        private const val REGION = "us-east-1"
        private const val ACCESS_KEY = "test"
        private const val SECRET_KEY = "test"

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
