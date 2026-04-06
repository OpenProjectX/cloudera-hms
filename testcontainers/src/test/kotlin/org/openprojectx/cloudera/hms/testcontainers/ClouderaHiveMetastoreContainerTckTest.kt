package org.openprojectx.cloudera.hms.testcontainers

import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import org.openprojectx.cloudera.hms.tck.AbstractReusableHiveMetastoreClientTck
import org.openprojectx.cloudera.hms.tck.HiveMetastoreUnderTest
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ClouderaHiveMetastoreContainerTckTest : AbstractReusableHiveMetastoreClientTck() {
    override fun metastoreUnderTest(): HiveMetastoreUnderTest {
        val container = ClouderaHiveMetastoreContainer()
            .withDatabaseName("metastore_db")
            .withDatabaseUser("hive")
            .withDatabasePassword("hive-password")
            .withWarehouseDir(DEFAULT_WAREHOUSE_DIR.toString())
            .withLogLevel("DEBUG")
            .apply { start() }

        return object : HiveMetastoreUnderTest {
            override val warehouseDir: Path = DEFAULT_WAREHOUSE_DIR

            override fun createClient(): HiveMetaStoreClient =
                HiveMetaStoreClient(
                    MetastoreConf.newMetastoreConf().apply {
                        MetastoreConf.setVar(this, MetastoreConf.ConfVars.THRIFT_URIS, container.thriftUri())
                        MetastoreConf.setTimeVar(this, MetastoreConf.ConfVars.CLIENT_SOCKET_TIMEOUT, 30, TimeUnit.SECONDS)
                        set("hive.metastore.uris", container.thriftUri())
                        set("metastore.thrift.uris", container.thriftUri())
                        set("fs.defaultFS", "file:///")
                    }
                )

            override fun close() {
                container.stop()
            }
        }
    }

    companion object {
        private val DEFAULT_WAREHOUSE_DIR: Path = Path.of("/var/lib/cloudera-hms/warehouse")
    }
}
