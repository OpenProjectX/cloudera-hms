package org.openprojectx.cloudera.hms.tck

class CoreClasspathMariaDbHiveMetastoreClientTckTest : AbstractHiveMetastoreClientTck() {
    override fun databaseType(): String = "mariadb"
}
