package org.openprojectx.cloudera.hms.core

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hive.metastore.conf.MetastoreConf
import java.util.concurrent.TimeUnit

object HiveMetastoreConfigurations {
    fun newServerConfiguration(config: ClouderaHiveMetastoreConfig): Configuration =
        MetastoreConf.newMetastoreConf().apply {
            MetastoreConf.setVar(this, MetastoreConf.ConfVars.THRIFT_BIND_HOST, config.host)
            setInt(MetastoreConf.ConfVars.SERVER_PORT.varname, config.port)
            set(MetastoreConf.ConfVars.CONNECT_URL_KEY.varname, config.jdbcUrl)
            set(MetastoreConf.ConfVars.CONNECTION_DRIVER.varname, config.jdbcDriver)
            set(MetastoreConf.ConfVars.CONNECTION_USER_NAME.varname, config.jdbcUser)
            set(MetastoreConf.ConfVars.PWD.varname, config.jdbcPassword)
            setBoolean(MetastoreConf.ConfVars.SCHEMA_VERIFICATION.varname, false)
            setBoolean(MetastoreConf.ConfVars.SCHEMA_VERIFICATION_RECORD_VERSION.varname, true)
            setBoolean(MetastoreConf.ConfVars.AUTO_CREATE_ALL.varname, false)
            setBoolean(MetastoreConf.ConfVars.METRICS_ENABLED.varname, false)
            setBoolean(MetastoreConf.ConfVars.COMPACTOR_INITIATOR_ON.varname, false)
            setInt(MetastoreConf.ConfVars.COMPACTOR_WORKER_THREADS.varname, 0)
            setBoolean(MetastoreConf.ConfVars.METASTORE_HOUSEKEEPING_THREADS_ON.varname, false)
            set(MetastoreConf.ConfVars.TASK_THREADS_ALWAYS.varname, "")
            set(MetastoreConf.ConfVars.TASK_THREADS_REMOTE_ONLY.varname, "")
            setBoolean(MetastoreConf.ConfVars.EXECUTE_SET_UGI.varname, false)
            set("fs.defaultFS", "file:///")
            set("hive.metastore.warehouse.dir", config.warehouseDir.toUri().toString())
            set("metastore.warehouse.dir", config.warehouseDir.toUri().toString())
            set("datanucleus.schema.autoCreateAll", "false")
            set("datanucleus.autoCreateSchema", "false")
        }

    fun newClientConfiguration(config: ClouderaHiveMetastoreConfig): Configuration =
        MetastoreConf.newMetastoreConf().apply {
            MetastoreConf.setVar(this, MetastoreConf.ConfVars.THRIFT_URIS, config.thriftUri)
            MetastoreConf.setTimeVar(this, MetastoreConf.ConfVars.CLIENT_SOCKET_TIMEOUT, 30, TimeUnit.SECONDS)
            set("hive.metastore.uris", config.thriftUri)
            set("metastore.thrift.uris", config.thriftUri)
            set("fs.defaultFS", "file:///")
        }
}
