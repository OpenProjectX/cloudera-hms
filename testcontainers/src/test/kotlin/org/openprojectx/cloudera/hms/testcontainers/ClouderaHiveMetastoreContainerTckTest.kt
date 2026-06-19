package org.openprojectx.cloudera.hms.testcontainers

import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import org.openprojectx.cloudera.hms.tck.AbstractReusableHiveMetastoreClientTck
import org.openprojectx.cloudera.hms.tck.HiveMetastoreUnderTest
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ClouderaHiveMetastoreContainerTckTest : AbstractClouderaHiveMetastoreContainerTckTest()

class ClouderaHiveMetastoreMariaDbContainerTckTest : AbstractClouderaHiveMetastoreContainerTckTest() {
    override fun dockerImageName(): DockerImageName =
        DockerImageName.parse(
            System.getenv("CLOUDERA_HMS_TEST_MARIADB_IMAGE")
                ?.takeIf(String::isNotBlank)
                ?: "${ClouderaHiveMetastoreContainer.DEFAULT_IMAGE}-mariadb"
        )

    override fun warehouseDir(): Path = Path.of("/var/lib/mysql/cloudera_hms/warehouse")
}

abstract class AbstractClouderaHiveMetastoreContainerTckTest : AbstractReusableHiveMetastoreClientTck() {
    override fun metastoreUnderTest(): HiveMetastoreUnderTest {
        val container = ClouderaHiveMetastoreContainer(dockerImageName())
            .withDatabaseName("metastore_db")
            .withDatabaseUser("hive")
            .withDatabasePassword("hive-password")
            .withWarehouseDir(warehouseDir().toString())
            .withLogLevel("DEBUG")
            .apply { start() }

        return object : HiveMetastoreUnderTest {
            override val warehouseDir: Path = warehouseDir()

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

    protected open fun dockerImageName(): DockerImageName = ClouderaHiveMetastoreContainer.imageName()

    protected open fun warehouseDir(): Path = DEFAULT_WAREHOUSE_DIR

    companion object {
        private val DEFAULT_WAREHOUSE_DIR: Path = Path.of("/var/lib/cloudera-hms/warehouse")
    }
}
