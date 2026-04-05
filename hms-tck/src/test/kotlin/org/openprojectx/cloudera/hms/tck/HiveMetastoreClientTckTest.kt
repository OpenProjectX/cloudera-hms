package org.openprojectx.cloudera.hms.org.openprojectx.cloudera.hms.tck

import org.apache.hadoop.hive.metastore.TableType
import org.apache.hadoop.hive.metastore.api.Database
import org.apache.hadoop.hive.metastore.api.FieldSchema
import org.apache.hadoop.hive.metastore.api.Partition
import org.apache.hadoop.hive.metastore.api.SerDeInfo
import org.apache.hadoop.hive.metastore.api.StorageDescriptor
import org.apache.hadoop.hive.metastore.api.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.cloudera.hms.core.ClouderaHiveMetastoreProcess
import org.openprojectx.cloudera.hms.junit5.ClouderaHiveMetastoreTest
import java.util.UUID
import kotlin.use

@ClouderaHiveMetastoreTest
class HiveMetastoreClientTckTest {
    @Test
    fun `supports core Hive metastore lifecycle operations`(metastore: ClouderaHiveMetastoreProcess) {
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
                        setCols(listOf(
                            FieldSchema("event_id", "string", null),
                            FieldSchema("user_id", "bigint", null),
                        ))
                        setInputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat")
                        setOutputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat")
                        setSerdeInfo(SerDeInfo().apply {
                            setSerializationLib("org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe")
                        })
                        setLocation(metastore.config.warehouseDir.resolve("$databaseName.db/$tableName").toUri().toString())
                    })
                }
            )

            client.add_partition(
                Partition().apply {
                    setDbName(databaseName)
                    setTableName(tableName)
                    setValues(listOf("2026-04-05"))
                    setSd(client.getTable(databaseName, tableName).sd.deepCopy().apply {
                        setLocation(metastore.config.warehouseDir
                            .resolve("$databaseName.db/$tableName/dt=2026-04-05")
                            .toUri()
                            .toString())
                    })
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