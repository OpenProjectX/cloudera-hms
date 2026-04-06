package org.openprojectx.cloudera.hms.testcontainers

import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.api.Database
import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers
class ClouderaHiveMetastoreContainerTest {
    @Container
    private val metastore = ClouderaHiveMetastoreContainer()
        .withDatabaseName("metastore_db")
        .withDatabaseUser("hive")
        .withDatabasePassword("hive-password")
        .withLogLevel("DEBUG")

    @Test
    fun `starts the built image and accepts Hive metastore clients`() {
        val databaseName = "db_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        HiveMetaStoreClient(
            MetastoreConf.newMetastoreConf().apply {
                MetastoreConf.setVar(this, MetastoreConf.ConfVars.THRIFT_URIS, metastore.thriftUri())
                MetastoreConf.setTimeVar(this, MetastoreConf.ConfVars.CLIENT_SOCKET_TIMEOUT, 30, TimeUnit.SECONDS)
                set("hive.metastore.uris", metastore.thriftUri())
                set("metastore.thrift.uris", metastore.thriftUri())
                set("fs.defaultFS", "file:///")
            }
        ).use { client ->
            client.createDatabase(Database().apply { name = databaseName })
            assertTrue(client.getAllDatabases().contains(databaseName))
        }
    }
}
