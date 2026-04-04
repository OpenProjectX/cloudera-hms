package org.openprojectx.data.metastore.org.openprojectx.data.metastore

import org.apache.hadoop.hive.metastore.TableType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class HiveMetastoreClientIntegrationTest {

    private lateinit var client: HiveMetastoreClient

    private lateinit var dbName: String
    private lateinit var tableName: String

    @BeforeEach
    fun setUp() {
        val metastoreUri = System.getProperty("hms.uri")
            ?: System.getenv("HMS_URI")
            ?: "thrift://localhost:9083"

        client = HiveMetastoreClient(
            HiveMetastoreClient.ClientConfig(
                uri = metastoreUri,
                saslEnabled = false,
            )
        )

        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        dbName = "it_db_$suffix"
        tableName = "it_table_$suffix"
    }

    @AfterEach
    fun tearDown() {
        runCatching {
            if (client.tableExists(dbName, tableName)) {
                client.dropTable(dbName, tableName, deleteData = false)
            }
        }

        runCatching {
            if (client.databaseExists(dbName)) {
                client.dropDatabase(dbName, deleteData = false, cascade = true)
            }
        }

        runCatching { client.close() }
    }

    @Test
    fun `should create and fetch database`() {
        assertFalse(client.databaseExists(dbName))

        client.createDatabase(
            name = dbName,
            description = "integration test database",
        )

        assertTrue(client.databaseExists(dbName))

        val db = client.getDatabase(dbName)
        assertEquals(dbName, db.name)
        assertEquals("integration test database", db.description)
    }

    @Test
    fun `should create external table and verify metadata`() {
        client.createDatabase(dbName)

        client.createExternalTable(
            db = dbName,
            name = tableName,
            columns = listOf(
                fieldSchema("event_id", "string", "event id"),
                fieldSchema("user_id", "bigint", "user id"),
                fieldSchema("payload", "string", "json payload"),
            ),
            partitionKeys = listOf(
                fieldSchema("dt", "string", "partition date"),
            ),
            location = "file:///tmp/hms-it/$dbName/$tableName",
        )

        assertTrue(client.tableExists(dbName, tableName))

        val table = client.getTable(dbName, tableName)
        assertEquals(dbName, table.dbName)
        assertEquals(tableName, table.tableName)
        assertEquals(TableType.EXTERNAL_TABLE.name, table.tableType)
        assertEquals("file:/tmp/hms-it/$dbName/$tableName", table.sd.location)
        assertEquals(listOf("event_id", "user_id", "payload"), table.sd.cols.map { it.name })
        assertEquals(listOf("dt"), table.partitionKeys.map { it.name })
        assertEquals("TRUE", table.parameters["EXTERNAL"])
    }

    @Test
    fun `should add rename and drop columns`() {
        client.createDatabase(dbName)

        client.createExternalTable(
            db = dbName,
            name = tableName,
            columns = listOf(
                fieldSchema("id", "string"),
                fieldSchema("payload", "string"),
            ),
            location = "file:///tmp/hms-it/$dbName/$tableName",
        )

        client.addColumns(
            db = dbName,
            tableName = tableName,
            newCols = listOf(fieldSchema("session_id", "string")),
        )

        var table = client.getTable(dbName, tableName)
        assertEquals(listOf("id", "payload", "session_id"), table.sd.cols.map { it.name })

        client.renameColumn(
            db = dbName,
            tableName = tableName,
            oldName = "payload",
            newName = "raw_payload",
        )

        table = client.getTable(dbName, tableName)
        assertEquals(listOf("id", "raw_payload", "session_id"), table.sd.cols.map { it.name })

        client.dropColumn(
            db = dbName,
            tableName = tableName,
            colName = "session_id",
        )

        table = client.getTable(dbName, tableName)
        assertEquals(listOf("id", "raw_payload"), table.sd.cols.map { it.name })
    }

    @Test
    fun `should add and fetch partition`() {
        client.createDatabase(dbName)

        client.createExternalTable(
            db = dbName,
            name = tableName,
            columns = listOf(
                fieldSchema("event_id", "string"),
                fieldSchema("payload", "string"),
            ),
            partitionKeys = listOf(
                fieldSchema("dt", "string"),
            ),
            location = "file:///tmp/hms-it/$dbName/$tableName",
        )

        assertFalse(client.partitionExists(dbName, tableName, listOf("2024-06-01")))

        val partition = client.addPartition(
            db = dbName,
            tableName = tableName,
            values = listOf("2024-06-01"),
        )

        assertNotNull(partition)
        assertTrue(client.partitionExists(dbName, tableName, listOf("2024-06-01")))

        val fetched = client.getPartition(dbName, tableName, listOf("2024-06-01"))
        assertEquals(listOf("2024-06-01"), fetched.values)
        assertTrue(fetched.sd.location.endsWith("/dt=2024-06-01"))

        val names = client.listPartitionNames(dbName, tableName)
        assertEquals(listOf("dt=2024-06-01"), names)
    }

    @Test
    fun `should rename table`() {
        client.createDatabase(dbName)

        client.createExternalTable(
            db = dbName,
            name = tableName,
            columns = listOf(fieldSchema("id", "string")),
            location = "file:///tmp/hms-it/$dbName/$tableName",
        )

        val newTableName = "${tableName}_renamed"

        client.renameTable(dbName, tableName, newTableName)

        assertFalse(client.tableExists(dbName, tableName))
        assertTrue(client.tableExists(dbName, newTableName))

        client.dropTable(dbName, newTableName, deleteData = false)
    }
}