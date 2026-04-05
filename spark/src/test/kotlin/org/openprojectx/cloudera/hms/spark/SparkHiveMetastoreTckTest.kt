package org.openprojectx.cloudera.hms.spark

import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.Metadata
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreProcess
import org.openprojectx.cloudera.hms.junit5.ClouderaHiveMetastoreExtension
import java.sql.Timestamp
import java.util.UUID

@ExtendWith(ClouderaHiveMetastoreExtension::class)
class SparkHiveMetastoreTckTest {
    @Test
    fun `spark can create alter and query Hive metadata through cloudera hms`(metastore: ClouderaHiveMetastoreProcess) {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val databaseName = "spark_db_$suffix"
        val tableName = "events"
        val renamedTableName = "events_v2"
        val qualifiedTableName = "$databaseName.$tableName"
        val renamedQualifiedTableName = "$databaseName.$renamedTableName"

        val spark = SparkSession.builder()
            .appName("cloudera-hms-spark-tck")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.shuffle.partitions", "1")
            .config("spark.sql.warehouse.dir", metastore.config.warehouseDir.toUri().toString())
            .config("spark.hadoop.hive.metastore.uris", metastore.config.thriftUri)
            .config("hive.metastore.uris", metastore.config.thriftUri)
            .config("spark.sql.catalogImplementation", "hive")
            .enableHiveSupport()
            .getOrCreate()

        spark.sparkContext().setLogLevel("WARN")

        try {
            spark.sql("CREATE DATABASE $databaseName")
            spark.sql("USE $databaseName")
            spark.sql(
                """
                CREATE TABLE $qualifiedTableName (
                    event_id STRING,
                    user_id BIGINT,
                    created_at TIMESTAMP
                )
                USING PARQUET
                PARTITIONED BY (dt STRING)
                """.trimIndent()
            )

            val schema = StructType(
                arrayOf(
                    StructField("event_id", DataTypes.StringType, false, Metadata.empty()),
                    StructField("user_id", DataTypes.LongType, false, Metadata.empty()),
                    StructField("created_at", DataTypes.TimestampType, false, Metadata.empty()),
                    StructField("dt", DataTypes.StringType, false, Metadata.empty()),
                )
            )

            val rows = listOf(
                RowFactory.create("e1", 100L, Timestamp.valueOf("2026-04-05 08:00:00"), "2026-04-05"),
                RowFactory.create("e2", 200L, Timestamp.valueOf("2026-04-05 09:00:00"), "2026-04-05"),
                RowFactory.create("e3", 300L, Timestamp.valueOf("2026-04-06 09:00:00"), "2026-04-06"),
            )

            spark.createDataFrame(rows, schema)
                .write()
                .mode(SaveMode.Append)
                .format("parquet")
                .insertInto(qualifiedTableName)

            assertEquals(3L, spark.table(qualifiedTableName).count())

            spark.sql("ALTER TABLE $qualifiedTableName ADD COLUMNS (source STRING)")
            spark.sql("ALTER TABLE $qualifiedTableName RENAME TO $renamedQualifiedTableName")

            val partitions = spark.sql("SHOW PARTITIONS $renamedQualifiedTableName")
                .collectAsList()
                .map { it.getString(0) }
                .sorted()

            assertEquals(listOf("dt=2026-04-05", "dt=2026-04-06"), partitions)

            val counts = spark.sql(
                """
                SELECT dt, COUNT(*) AS cnt
                FROM $renamedQualifiedTableName
                GROUP BY dt
                ORDER BY dt
                """.trimIndent()
            ).collectAsList()

            assertEquals(2L, counts[0].getLong(1))
            assertEquals(1L, counts[1].getLong(1))
            assertTrue(spark.sql("SHOW TABLES IN $databaseName").collectAsList().any { it.getString(1) == renamedTableName })
        } finally {
            try {
                spark.sql("DROP DATABASE IF EXISTS $databaseName CASCADE")
            } finally {
                spark.stop()
            }
        }
    }
}
