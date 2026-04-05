package org.openprojectx.cloudera.hms.junit5

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ExtendWith(ClouderaHiveMetastoreExtension::class)
annotation class ClouderaHiveMetastoreTest(
    val postgresImage: String = "postgres:14",
    val schemaSqlPath: String = "",
    val logLevel: String = "INFO",
)
