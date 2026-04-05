package org.openprojectx.data.metastore

import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import java.util.UUID

fun main() {
    val hmsUri = System.getProperty("hms.uri")
        ?: System.getenv("HMS_URI")
        ?: "thrift://localhost:9083"

    val warehouseDir = System.getProperty("spark.sql.warehouse.dir")
        ?: "file:///tmp/spark-warehouse"

    val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
    val dbName = "spark_it_db_$suffix"
    val tableName = "events"
    val fullTableName = "$dbName.$tableName"

    val spark = SparkSession.builder()
        .appName("Spark HMS Integration")
        .master("local[*]")
        .config("spark.sql.warehouse.dir", warehouseDir)
        .config("hive.metastore.uris", hmsUri)
        .config("spark.sql.catalogImplementation", "hive")
        .enableHiveSupport()
        .getOrCreate()

    spark.sparkContext().setLogLevel("WARN")

    try {
        println("Using Hive Metastore at: $hmsUri")
        println("Using warehouse dir     : $warehouseDir")

        // 1) Create database
        spark.sql("CREATE DATABASE IF NOT EXISTS $dbName")
        spark.sql("SHOW DATABASES").show(false)

        // 2) Switch to database
        spark.sql("USE $dbName")

        // 3) Drop table if leftover from previous run
        spark.sql("DROP TABLE IF EXISTS $fullTableName")

        // 4) Create partitioned table in HMS
        spark.sql(
            """
            CREATE TABLE $fullTableName (
                event_id STRING,
                user_id BIGINT,
                payload STRING,
                created_at TIMESTAMP
            )
            USING PARQUET
            PARTITIONED BY (dt STRING)
            """.trimIndent()
        )

        println("=== Table metadata after create ===")
        spark.sql("DESCRIBE EXTENDED $fullTableName").show(200, false)

        // 5) Insert sample data through DataFrame writer
        val schema = StructType(
            arrayOf(
                StructField("event_id", DataTypes.StringType, false,org.apache.spark.sql.types.Metadata.empty() ),
                StructField("user_id", DataTypes.LongType, false, org.apache.spark.sql.types.Metadata.empty()),
                StructField("payload", DataTypes.StringType, true, org.apache.spark.sql.types.Metadata.empty()),
                StructField("created_at", DataTypes.TimestampType, false, org.apache.spark.sql.types.Metadata.empty()),
                StructField("dt", DataTypes.StringType, false, org.apache.spark.sql.types.Metadata.empty())
            )
        )

        val rows = listOf(
            RowFactory.create("e1", 101L, """{"type":"click"}""", java.sql.Timestamp.valueOf("2024-06-01 10:00:00"), "2024-06-01"),
            RowFactory.create("e2", 102L, """{"type":"view"}""", java.sql.Timestamp.valueOf("2024-06-01 11:00:00"), "2024-06-01"),
            RowFactory.create("e3", 201L, """{"type":"purchase"}""", java.sql.Timestamp.valueOf("2024-06-02 09:30:00"), "2024-06-02")
        )

        val df = spark.createDataFrame(rows, schema)

        df.write()
            .mode(SaveMode.Append)
            .format("parquet")
            .insertInto(fullTableName)

        println("=== Data after insert ===")
        spark.sql("SELECT * FROM $fullTableName ORDER BY dt, event_id").show(false)

        // 6) Show partitions
        println("=== Partitions ===")
        spark.sql("SHOW PARTITIONS $fullTableName").show(false)

        // 7) Add a new column
        spark.sql("ALTER TABLE $fullTableName ADD COLUMNS (session_id STRING)")
        println("=== Schema after ADD COLUMNS ===")
        spark.sql("DESCRIBE $fullTableName").show(false)

        // 8) Rename table
        val renamedTable = "${tableName}_renamed"
        val renamedFullTable = "$dbName.$renamedTable"
        spark.sql("ALTER TABLE $fullTableName RENAME TO $renamedFullTable")

        println("=== Tables after rename ===")
        spark.sql("SHOW TABLES IN $dbName").show(false)

        // 9) Query renamed table
        println("=== Aggregation on renamed table ===")
        spark.sql(
            """
            SELECT dt, COUNT(*) AS cnt, COUNT(DISTINCT user_id) AS users
            FROM $renamedFullTable
            GROUP BY dt
            ORDER BY dt
            """.trimIndent()
        ).show(false)

        // 10) Add a partition manually
        spark.sql(
            """
            ALTER TABLE $renamedFullTable
            ADD IF NOT EXISTS PARTITION (dt='2024-06-03')
            """.trimIndent()
        )

        println("=== Partitions after manual add ===")
        spark.sql("SHOW PARTITIONS $renamedFullTable").show(false)

        // 11) Repair metadata if you place files externally and want discovery
        // spark.sql("MSCK REPAIR TABLE $renamedFullTable")

        // 12) Optional: set table properties
//        spark.sql(
//            """
//            ALTER TABLE $renamedFullTable
//            SET TBLPROPERTIES (
//                'owner'='integration-test',
//                'quality'='gold'
//            )
//            """.trimIndent()
//        )

        println("=== Extended metadata after property update ===")
        spark.sql("DESCRIBE EXTENDED $renamedFullTable").show(200, false)

        // 13) Drop one partition
        spark.sql(
            """
            ALTER TABLE $renamedFullTable
            DROP IF EXISTS PARTITION (dt='2024-06-03')
            """.trimIndent()
        )

        println("=== Partitions after drop ===")
        spark.sql("SHOW PARTITIONS $renamedFullTable").show(false)

        // 14) Final read smoke test
        val row = spark.sql("SELECT COUNT(*) AS total FROM $renamedFullTable").first()
        val count = row.getAs<Long>("total")
        println("Final row count: $count")

        // 15) Cleanup
        spark.sql("DROP TABLE IF EXISTS $renamedFullTable")
        spark.sql("DROP DATABASE IF EXISTS $dbName CASCADE")
    } finally {
        spark.stop()
    }
}