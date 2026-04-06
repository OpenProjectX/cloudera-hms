# cloudera-hms

Standalone Cloudera-compatible Hive Metastore for local development, implemented in Kotlin and aligned to the Cloudera Spark stack.

## Modules

- `core`: metastore runtime, PostgreSQL schema bootstrap, and configuration helpers
- `runtime`: shaded standalone runtime jar for launching the metastore
- `image`: Jib-based container image assembly for a combined PostgreSQL plus Hive metastore runtime
- `junit5`: annotation-driven JUnit 5 support that provisions PostgreSQL and starts the metastore for tests
- `hms-tck-core`: reusable Java 11-compatible Hive metastore TCK contract and assertions
- `hms-tck`: Java 17 TCK implementations for the in-process `core` and shaded `runtime` executions
- `testcontainers`: JDK 11 Testcontainers wrapper for the built metastore image
- `spark`: Spark-facing TCKs that validate Spark SQL and Iceberg against the metastore

## Getting Started

Start a local PostgreSQL if you want to run the metastore against a local database:

```bash
docker compose up -d
```

Build the shaded runtime:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :runtime:shadowJar
```

Build the container image:

```bash
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuild
```

For contributor-oriented build commands, version alignment, and verification notes, see [CONTRIBUTING.md](/data/Git/cloudera-hms/CONTRIBUTING.md).

## Configuration

The runtime expects these JVM system properties:

- `cloudera.hms.host`
- `cloudera.hms.port`
- `cloudera.hms.warehouse.dir`
- `cloudera.hms.jdbc.url`
- `cloudera.hms.jdbc.user`
- `cloudera.hms.jdbc.password`

Optional properties:

- `cloudera.hms.jdbc.driver`
- `cloudera.hms.initialize-schema`
- `cloudera.hms.schema.resource`
- `cloudera.hms.schema.file`

`cloudera.hms.schema.file` takes precedence when you want to supply your own schema SQL.

When starting the server through the Kotlin API, [ClouderaHiveMetastoreConfig.kt](/data/Git/cloudera-hms/core/src/main/kotlin/org/openprojectx/cloudera/hms/core/ClouderaHiveMetastoreConfig.kt) also exposes:

- `extraConfiguration` for arbitrary Hive or Hadoop properties that need to exist inside the metastore JVM
- `logLevel` for generated HMS server logging
- `logConfigFile` for a complete custom Log4j 2 properties file

Default configuration is defined in [ClouderaHiveMetastoreConfig.kt](/data/Git/cloudera-hms/core/src/main/kotlin/org/openprojectx/cloudera/hms/core/ClouderaHiveMetastoreConfig.kt). Server bootstrap happens in [HiveMetastoreServerMain.kt](/data/Git/cloudera-hms/core/src/main/kotlin/org/openprojectx/cloudera/hms/core/HiveMetastoreServerMain.kt).

## JUnit 5

The `junit5` module provides [ClouderaHiveMetastoreTest.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreTest.kt), which starts PostgreSQL plus a metastore process for a test class.

Add the dependency:

```kotlin
dependencies {
    testImplementation("org.openprojectx.cloudera.hms:junit5:<version>")
}
```

Use the annotation:

```kotlin
@ClouderaHiveMetastoreTest(
    postgresImage = "postgres:14",
    schemaSqlPath = "/hive-schema-3.1.3000.postgres.sql",
    logLevel = "DEBUG",
)
class MyMetastoreTest
```

Supported annotation attributes:

- `postgresImage`: overrides the PostgreSQL Testcontainers image
- `schemaSqlPath`: accepts either a filesystem path or a classpath resource path
- `logLevel`: configures the generated HMS server Log4j 2 root level

## Testcontainers

The `testcontainers` module wraps the built metastore image for integration tests on JDK 11+.

Add the dependency:

```kotlin
dependencies {
    testImplementation("org.openprojectx.cloudera.hms:testcontainers:<version>")
}
```

Use the default image:

```kotlin
val metastore = ClouderaHiveMetastoreContainer()
    .withDatabaseName("metastore_db")
    .withDatabaseUser("hive")
    .withDatabasePassword("hive-password")
```

Use a custom image explicitly:

```kotlin
val metastore = ClouderaHiveMetastoreContainer.withImage("my-registry/cloudera-hms:test")
    .withDatabaseName("metastore_db")
    .withDatabaseUser("hive")
    .withDatabasePassword("hive-password")
```

Or set `CLOUDERA_HMS_TEST_IMAGE` and continue using the default constructor.

The main entry point is [ClouderaHiveMetastoreContainer.kt](/data/Git/cloudera-hms/testcontainers/src/main/kotlin/org/openprojectx/cloudera/hms/testcontainers/ClouderaHiveMetastoreContainer.kt). The module also reuses the shared TCK from `hms-tck-core` for its own integration coverage.

## Container image

The `image` module builds a runnable container image with Jib. The image expects a base image that already includes:

- PostgreSQL
- JDK 17
- the standard PostgreSQL container entrypoint at `/usr/local/bin/docker-entrypoint.sh`

Build configuration is environment-variable driven:

- `CLOUDERA_HMS_BASE_IMAGE`
- `CLOUDERA_HMS_IMAGE`
- `CLOUDERA_HMS_IMAGE_TAGS`

Runtime configuration is environment-variable driven. The image supports:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`
- `HMS_HOST`
- `HMS_PORT`
- `HMS_WAREHOUSE_DIR`
- `HMS_JDBC_URL`
- `HMS_JDBC_USER`
- `HMS_JDBC_PASSWORD`
- `HMS_JDBC_DRIVER`
- `HMS_INITIALIZE_SCHEMA`
- `HMS_SCHEMA_RESOURCE`
- `HMS_SCHEMA_FILE`
- `HMS_EXTRA_CONFIG_FILE`
- `HMS_EXTRA_CONF`
- `HMS_LOG_LEVEL`
- `JAVA_OPTS`

Extra Hive or Hadoop properties can be passed either as newline-delimited `HMS_EXTRA_CONF` entries or as individual `HMS_CONF_*` environment variables, where `_` maps to `.` and `__` maps to `-`.
