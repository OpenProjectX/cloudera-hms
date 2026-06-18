package org.openprojectx.cloudera.hms.junit5

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ExtendWith(ClouderaHiveMetastoreExtension::class)
annotation class ClouderaHiveMetastoreTest(
    val databaseType: String = "postgresql",
    val postgresImage: String = "postgres:14",
    val mariadbImage: String = "mariadb:10.6.24-ubi9",
    val schemaSqlPath: String = "",
    val logLevel: String = "INFO",
)
