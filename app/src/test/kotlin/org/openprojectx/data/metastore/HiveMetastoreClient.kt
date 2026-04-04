package org.openprojectx.data.metastore.org.openprojectx.data.metastore

import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.TableType
import org.apache.hadoop.hive.metastore.api.*
//import org.apache.hadoop.hive.metastore.conf.MetastoreConf
//import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class HiveMetastoreClient(
    config: ClientConfig,
    private val metastoreClientFactory: (HiveConf) -> HiveMetaStoreClient = ::HiveMetaStoreClient,
) : AutoCloseable {

    data class ClientConfig(
        val uri: String,
        val principal: String? = null,
        val socketTimeoutMs: Int = 60_000,
        val connectionRetries: Int = 3,
        val saslEnabled: Boolean = false,
    )

    private val log = LoggerFactory.getLogger(javaClass)

    private val client: HiveMetaStoreClient = run {
        val conf = HiveConf().apply {
            set(HiveConf.ConfVars.METASTOREURIS.varname, config.uri)
            setInt(
                HiveConf.ConfVars.METASTORE_CLIENT_SOCKET_TIMEOUT.varname,
                TimeUnit.MILLISECONDS.toSeconds(config.socketTimeoutMs.toLong()).toInt()
            )
//            setInt(MetastoreConf.ConfVars.THRIFT_CONNECTION_RETRIES.varname, config.connectionRetries)
            if (config.saslEnabled && config.principal != null) {
                set(HiveConf.ConfVars.METASTORE_KERBEROS_PRINCIPAL.varname, config.principal)
                set("hive.metastore.sasl.enabled", "true")
                set("hadoop.security.authentication", "kerberos")
            }
        }
        metastoreClientFactory(conf)
    }

    // ──────────────────────────────────────────────
    // Database operations
    // ──────────────────────────────────────────────

    fun createDatabase(
        name: String,
        description: String = "",
        locationUri: String? = null,
        params: Map<String, String> = emptyMap(),
    ) {
        val db = Database(name, description, locationUri, params)
        client.createDatabase(db)
        log.info("Created database: $name")
    }

    fun getDatabase(name: String): Database = client.getDatabase(name)

    fun listDatabases(pattern: String = "*"): List<String> = client.getDatabases(pattern)

    fun databaseExists(name: String): Boolean = runCatching { client.getDatabase(name) }.isSuccess

    fun alterDatabase(name: String, params: Map<String, String>) {
        val db = client.getDatabase(name)
        db.parameters = params.toMutableMap()
        client.alterDatabase(name, db)
    }

    fun dropDatabase(name: String, deleteData: Boolean = false, cascade: Boolean = false) {
        client.dropDatabase(name, deleteData, cascade)
        log.info("Dropped database: $name (deleteData=$deleteData, cascade=$cascade)")
    }

    // ──────────────────────────────────────────────
    // Table operations
    // ──────────────────────────────────────────────

    fun createTable(table: Table) {
        client.createTable(table)
        log.info("Created table: ${table.dbName}.${table.tableName}")
    }

    /** Convenience builder for a common external table. */
    fun createExternalTable(
        db: String,
        name: String,
        columns: List<FieldSchema>,
        partitionKeys: List<FieldSchema> = emptyList(),
        location: String,
        inputFormat: String = "org.apache.hadoop.mapred.TextInputFormat",
        outputFormat: String = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
        serdeLib: String = "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe",
        serdeParams: Map<String, String> = mapOf("serialization.format" to "1"),
        tableParams: Map<String, String> = emptyMap(),
    ) {
        val sd = StorageDescriptor().apply {
            this.cols = columns
            this.location = location
            this.inputFormat = inputFormat
            this.outputFormat = outputFormat
            this.serdeInfo = SerDeInfo().apply {
                serializationLib = serdeLib
                parameters = serdeParams.toMutableMap()
            }
        }
        val table = Table().apply {
            dbName = db
            tableName = name
            tableType = TableType.EXTERNAL_TABLE.name
            this.sd = sd
            this.partitionKeys = partitionKeys
            parameters = (tableParams + mapOf("EXTERNAL" to "TRUE")).toMutableMap()
        }
        client.createTable(table)
        log.info("Created external table: $db.$name at $location")
    }

    fun getTable(db: String, name: String): Table = client.getTable(db, name)

    fun tableExists(db: String, name: String): Boolean = client.tableExists(db, name)

    fun listTables(db: String, pattern: String = "*"): List<String> = client.getTables(db, pattern)

    fun listTablesByType(db: String, tableType: TableType): List<String> =
        client.getTables(db, "*", tableType)

    fun alterTable(db: String, name: String, table: Table) {
        client.alter_table(db, name, table)
        log.info("Altered table: $db.$name")
    }

    fun renameTable(db: String, oldName: String, newName: String) {
        val table = client.getTable(db, oldName).apply { tableName = newName }
        client.alter_table(db, oldName, table)
        log.info("Renamed $db.$oldName → $db.$newName")
    }

    fun dropTable(db: String, name: String, deleteData: Boolean = false) {
        client.dropTable(db, name, deleteData, true)
        log.info("Dropped table: $db.$name (deleteData=$deleteData)")
    }

    fun truncateTable(db: String, name: String) {
//        client.truncateTable(db, name, emptyList())
    }

    // ──────────────────────────────────────────────
    // Schema evolution
    // ──────────────────────────────────────────────

    fun addColumns(db: String, tableName: String, newCols: List<FieldSchema>) {
        val table = client.getTable(db, tableName)
        table.sd.cols = table.sd.cols + newCols
        client.alter_table(db, tableName, table)
        log.info("Added ${newCols.size} column(s) to $db.$tableName")
    }

    fun dropColumn(db: String, tableName: String, colName: String) {
        val table = client.getTable(db, tableName)
        table.sd.cols = table.sd.cols.filterNot { it.name == colName }
        client.alter_table(db, tableName, table)
        log.info("Dropped column '$colName' from $db.$tableName")
    }

    fun renameColumn(db: String, tableName: String, oldName: String, newName: String) {
        val table = client.getTable(db, tableName)
        table.sd.cols = table.sd.cols.map { col ->
            if (col.name == oldName) FieldSchema(newName, col.type, col.comment) else col
        }
        client.alter_table(db, tableName, table)
        log.info("Renamed column '$oldName' → '$newName' in $db.$tableName")
    }

    fun replaceColumns(db: String, tableName: String, cols: List<FieldSchema>) {
        val table = client.getTable(db, tableName)
        table.sd.cols = cols
        client.alter_table(db, tableName, table)
    }

    // ──────────────────────────────────────────────
    // Partition management
    // ──────────────────────────────────────────────

    fun addPartition(db: String, tableName: String, values: List<String>, location: String? = null): Partition {
        val table = client.getTable(db, tableName)
        val partLocation = location ?: "${table.sd.location}/${
            table.partitionKeys.zip(values).joinToString("/") { (k, v) -> "${k.name}=$v" }
        }"
        val partition = Partition().apply {
            this.dbName = db
            this.tableName = tableName
            this.values = values
            this.sd = table.sd.deepCopy().apply { this.location = partLocation }
            this.parameters = mutableMapOf()
        }
        return client.add_partition(partition).also {
            log.info("Added partition $values to $db.$tableName")
        }
    }

    fun addPartitions(partitions: List<Partition>, ifNotExists: Boolean = true): List<Partition>  =
        client.add_partitions(partitions, ifNotExists, false)

    fun getPartition(db: String, tableName: String, values: List<String>): Partition =
        client.getPartition(db, tableName, values)

    fun listPartitions(db: String, tableName: String, maxParts: Short = -1): List<Partition> =
        client.listPartitions(db, tableName, maxParts)

    fun listPartitionNames(db: String, tableName: String): List<String> =
        client.listPartitionNames(db, tableName, Short.MAX_VALUE)

    fun partitionExists(db: String, tableName: String, values: List<String>): Boolean =
        runCatching { client.getPartition(db, tableName, values) }.isSuccess

    fun dropPartition(db: String, tableName: String, values: List<String>, deleteData: Boolean = false): Boolean =
        client.dropPartition(db, tableName, values, deleteData).also {
            log.info("Dropped partition $values from $db.$tableName (deleteData=$deleteData)")
        }

    /** Drop all partitions matching an expression, e.g. "dt < '2024-01-01'" */
//    fun dropPartitionsByFilter(db: String, tableName: String, filter: String, deleteData: Boolean = false): Int =
//        client.dropPartitions(db, tableName, listOf(filter), deleteData, false, false,).size

    fun alterPartition(db: String, tableName: String, partition: Partition) {
        client.alter_partition(db, tableName, partition)
    }

    fun getPartitionsByFilter(db: String, tableName: String, filter: String): List<Partition> =
        client.listPartitionsByFilter(db, tableName, filter, Short.MAX_VALUE)

    // ──────────────────────────────────────────────
    // Statistics
    // ──────────────────────────────────────────────

    fun getTableStats(db: String, tableName: String, colNames: List<String>): List<ColumnStatisticsObj> =
        client.getTableColumnStatistics(db, tableName, colNames)

    fun updateTableParams(db: String, tableName: String, params: Map<String, String>) {
        val table = client.getTable(db, tableName)
        table.parameters.putAll(params)
        client.alter_table(db, tableName, table)
    }

    // ──────────────────────────────────────────────
    // Functions
    // ──────────────────────────────────────────────

    fun createFunction(db: String, funcName: String, className: String, resources: List<ResourceUri> = emptyList()) {
        val func = Function().apply {
            functionName = funcName
            dbName = db
            this.className = className
            ownerType = PrincipalType.USER
            ownerName = "hive"
            functionType = FunctionType.JAVA
            resourceUris = resources
        }
        client.createFunction(func)
    }

    fun listFunctions(db: String, pattern: String = "*"): List<String> =
        client.getFunctions(db, pattern)

    fun dropFunction(db: String, funcName: String) {
        client.dropFunction(db, funcName)
    }

    // ──────────────────────────────────────────────
    // Roles & privileges
    // ──────────────────────────────────────────────

    fun createRole(roleName: String, ownerName: String = "admin") {
        client.create_role(Role(roleName, 0, ownerName))
    }

    fun dropRole(roleName: String) {
        client.drop_role(roleName)
    }

    fun listRoles(): List<Role> = client.list_roles(null, null)

    fun grantRoleToUser(roleName: String, userName: String) {
        client.grant_role(roleName, userName, PrincipalType.USER, "admin", PrincipalType.USER, false)
    }

    fun revokeRoleFromUser(roleName: String, userName: String) {
        client.revoke_role(roleName, userName, PrincipalType.USER, false)
    }

//    fun grantTablePrivilege(
//        db: String,
//        tableName: String,
//        grantee: String,
//        granteeType: PrincipalType,
//        privilege: PrivilegeGrantInfo,
//    ) {
//        val hiveObjectRef = HiveObjectRef().apply {
//            objectType = HiveObjectType.TABLE
//            dbName = db
//            this.objectName = tableName
//        }
//        val privBag =
//            PrivilegeBag(listOf(HiveObjectPrivilege(hiveObjectRef, grantee, granteeType, privilege, "authorizer")))
//        client.grant_privileges(privBag)
//    }

    // ──────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────

    fun getMetastoreVersion(): String? =
        runCatching { "client.metastoreVersion" }.getOrNull()

    override fun close() {
        runCatching { client.close() }
    }
}

// ──────────────────────────────────────────────
// DSL helpers
// ──────────────────────────────────────────────

fun fieldSchema(name: String, type: String, comment: String = "") = FieldSchema(name, type, comment)

fun resourceUri(type: ResourceType, uri: String) = ResourceUri(type, uri)

// ──────────────────────────────────────────────
// Usage example
// ──────────────────────────────────────────────

fun main() {
    val config = HiveMetastoreClient.ClientConfig(uri = "thrift://localhost:9083")

    HiveMetastoreClient(config).use { hms ->

        // --- Database ---
        if (!hms.databaseExists("lakehouse")) {
            hms.createDatabase("lakehouse", description = "Iceberg lakehouse")
        }

        // --- External table ---
        if (!hms.tableExists("lakehouse", "events")) {
            hms.createExternalTable(
                db = "lakehouse",
                name = "events",
                columns = listOf(
                    fieldSchema("event_id", "string", "UUID"),
                    fieldSchema("user_id", "bigint"),
                    fieldSchema("payload", "string", "JSON payload"),
                    fieldSchema("created_at", "timestamp"),
                ),
                partitionKeys = listOf(fieldSchema("dt", "string", "YYYY-MM-DD")),
                location = "s3a://my-bucket/lakehouse/events",
                serdeLib = "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe",
                tableParams = mapOf("parquet.compression" to "SNAPPY"),
            )
        }

        // --- Schema evolution ---
        hms.addColumns("lakehouse", "events", listOf(fieldSchema("session_id", "string")))

        // --- Partitions ---
        if (!hms.partitionExists("lakehouse", "events", listOf("2024-06-01"))) {
            hms.addPartition("lakehouse", "events", listOf("2024-06-01"))
        }
        println("Partitions: ${hms.listPartitionNames("lakehouse", "events")}")

        // --- Filter partitions ---
        val recent = hms.getPartitionsByFilter("lakehouse", "events", "dt >= '2024-06-01'")
        println("Recent partitions: ${recent.size}")

        // --- Roles ---
        hms.createRole("data_engineers")
        hms.grantRoleToUser("data_engineers", "alice")

        // --- Inspect ---
        val table = hms.getTable("lakehouse", "events")
        println("Table location : ${table.sd.location}")
        println("Columns        : ${table.sd.cols.map { it.name }}")
        println("Partition keys : ${table.partitionKeys.map { it.name }}")
    }
}