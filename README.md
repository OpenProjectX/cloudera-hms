# cloudera-hms

Standalone Cloudera-compatible Hive Metastore for local development, implemented in Kotlin and aligned to the Cloudera Spark stack.

## Getting Started

### JUnit 5

Add the dependency:

```kotlin
dependencies {
    testImplementation("org.openprojectx.cloudera.hms:junit5:<version>")
}
```

Use [ClouderaHiveMetastoreTest.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreTest.kt):

```kotlin
@ClouderaHiveMetastoreTest(
    databaseType = "postgresql",
    postgresImage = "postgres:14",
    schemaSqlPath = "/hive-schema-3.1.3000.postgres.sql",
    logLevel = "DEBUG",
)
class MyMetastoreTest
```

Supported annotation attributes:

- `databaseType`: `postgresql` by default; use `mariadb` for MariaDB
- `postgresImage`: overrides the PostgreSQL Testcontainers image
- `mariadbImage`: overrides the MariaDB Testcontainers image; defaults to `mariadb:10.6.24-ubi9`
- `schemaSqlPath`: accepts either a filesystem path or a classpath resource path
- `logLevel`: configures the generated HMS server Log4j 2 root level

For MariaDB-backed tests:

```kotlin
@ClouderaHiveMetastoreTest(
    databaseType = "mariadb",
    mariadbImage = "mariadb:10.6.24-ubi9",
    schemaSqlPath = "/hive-schema-3.1.3000.mysql.sql",
)
class MyMariaDbMetastoreTest
```

### Testcontainers

Add the dependency:

```kotlin
dependencies {
    testImplementation("org.openprojectx.cloudera.hms:testcontainers:<version>")
}
```

Use the default PostgreSQL image:

```kotlin
val metastore = ClouderaHiveMetastoreContainer()
    .withDatabaseType("postgresql")
    .withDatabaseName("metastore_db")
    .withDatabaseUser("hive")
    .withDatabasePassword("hive-password")
```

Use the MariaDB image:

```kotlin
val metastore = ClouderaHiveMetastoreContainer
    .withImage("ghcr.io/openprojectx/cloudera-hms:latest-mariadb")
    .withDatabaseType("mariadb")
    .withDatabaseName("metastore_db")
    .withDatabaseUser("hive")
    .withDatabasePassword("hive-password")
```

Use a custom image explicitly:

```kotlin
val metastore = ClouderaHiveMetastoreContainer.withImage("my-registry/cloudera-hms:test")
    .withDatabaseType("postgresql")
    .withDatabaseName("metastore_db")
    .withDatabaseUser("hive")
    .withDatabasePassword("hive-password")
```

Or set `CLOUDERA_HMS_TEST_IMAGE` and keep using the default constructor. For MariaDB, point it at a `-mariadb` image tag and set `HMS_DATABASE_TYPE` through the wrapper:

```bash
export CLOUDERA_HMS_TEST_IMAGE=ghcr.io/openprojectx/cloudera-hms:latest-mariadb
```

Typical JUnit 5 and Testcontainers usage:

```kotlin
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class MyPostgreSqlMetastoreContainerTest {
    @Container
    private val metastore = ClouderaHiveMetastoreContainer()
        .withDatabaseType("postgresql")
        .withDatabaseName("metastore_db")
        .withDatabaseUser("hive")
        .withDatabasePassword("hive-password")

    @Test
    fun testMetastore() {
        val thriftUri = metastore.thriftUri()
        // Create a HiveMetaStoreClient or your application client here.
    }
}
```

MariaDB JUnit 5 and Testcontainers usage:

```kotlin
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class MyMariaDbMetastoreContainerTest {
    @Container
    private val metastore = ClouderaHiveMetastoreContainer
        .withImage("ghcr.io/openprojectx/cloudera-hms:latest-mariadb")
        .withDatabaseType("mariadb")
        .withDatabaseName("metastore_db")
        .withDatabaseUser("hive")
        .withDatabasePassword("hive-password")

    @Test
    fun testMetastore() {
        val thriftUri = metastore.thriftUri()
        // Create a HiveMetaStoreClient or your application client here.
    }
}
```

### Build and run locally

Start a local PostgreSQL if you want to run the metastore against a local database:

```bash
docker compose up -d
```

Build the shaded runtime:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :runtime:shadowJar
```

Build both container image variants into the local Docker daemon:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuildAll
```

The PostgreSQL image uses `ghcr.io/openprojectx/postgres14-jdk17:latest` as its base and keeps the existing tag, for example `ghcr.io/openprojectx/cloudera-hms:<version>`. The MariaDB image uses `ghcr.io/openprojectx/mariadb10.6-jdk17:latest` as its base and gets a `-mariadb` tag suffix, for example `ghcr.io/openprojectx/cloudera-hms:<version>-mariadb`.

Build only one variant when needed:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuildPostgres
GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuildMariadb
```

Run the MariaDB image normally; it is built with `HMS_DATABASE_TYPE=mariadb` and defaults to `/hive-schema-3.1.3000.mysql.sql`, `org.mariadb.jdbc.Driver`, and a `jdbc:mariadb://127.0.0.1:3306/metastore_db?useMysqlMetadata=true` URL.

Run the PostgreSQL image with Docker:

```bash
docker run --rm \
  --name cloudera-hms \
  -p 9083:9083 \
  -p 5432:5432 \
  ghcr.io/openprojectx/cloudera-hms:latest
```

Run the MariaDB image with Docker:

```bash
docker run --rm \
  --name cloudera-hms-mariadb \
  -p 9083:9083 \
  -p 3306:3306 \
  ghcr.io/openprojectx/cloudera-hms:latest-mariadb
```

Both images expose the Hive Metastore Thrift service on `9083`. The PostgreSQL variant also exposes `5432`; the MariaDB variant also exposes `3306`.

Override the default database credentials when needed:

```bash
docker run --rm \
  --name cloudera-hms \
  -p 9083:9083 \
  -e POSTGRES_DB=metastore_db \
  -e POSTGRES_USER=hive \
  -e POSTGRES_PASSWORD=hive-password \
  ghcr.io/openprojectx/cloudera-hms:latest
```

```bash
docker run --rm \
  --name cloudera-hms-mariadb \
  -p 9083:9083 \
  -e MARIADB_DATABASE=metastore_db \
  -e MARIADB_USER=hive \
  -e MARIADB_PASSWORD=hive-password \
  ghcr.io/openprojectx/cloudera-hms:latest-mariadb
```

For contributor-oriented build commands, version alignment, and verification notes, see [CONTRIBUTING.md](/data/Git/cloudera-hms/CONTRIBUTING.md).

## Modules

- `core`: metastore runtime, PostgreSQL/MariaDB schema bootstrap, and configuration helpers
- `runtime`: shaded standalone runtime jar for launching the metastore
- `image`: Jib-based container image assembly for a combined database plus Hive metastore runtime
- `junit5`: annotation-driven JUnit 5 support that provisions PostgreSQL or MariaDB and starts the metastore for tests
- `hms-tck-core`: reusable Java 11-compatible Hive metastore TCK contract and assertions
- `hms-tck`: Java 17 TCK implementations for the in-process `core` and shaded `runtime` executions
- `testcontainers`: JDK 11 Testcontainers wrapper for the built metastore image
- `spark`: Spark-facing TCKs that validate Spark SQL and Iceberg against the metastore

## Configuration

The runtime expects these JVM system properties:

- `cloudera.hms.host`
- `cloudera.hms.port`
- `cloudera.hms.database.type`
- `cloudera.hms.warehouse.dir`
- `cloudera.hms.jdbc.url`
- `cloudera.hms.jdbc.driver`
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

The `junit5` module provides [ClouderaHiveMetastoreTest.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreTest.kt), which starts PostgreSQL or MariaDB plus a metastore process for a test class.

## Testcontainers

The `testcontainers` module wraps the built metastore image for integration tests on JDK 11+. The main entry point is [ClouderaHiveMetastoreContainer.kt](/data/Git/cloudera-hms/testcontainers/src/main/kotlin/org/openprojectx/cloudera/hms/testcontainers/ClouderaHiveMetastoreContainer.kt). The module also reuses the shared TCK from `hms-tck-core` for its own integration coverage.

## Container image

The `image` module builds a runnable container image with Jib. The image expects a base image that already includes one supported database:

- PostgreSQL or MariaDB
- JDK 17
- the standard PostgreSQL or MariaDB container entrypoint at `/usr/local/bin/docker-entrypoint.sh`

Build configuration is environment-variable driven:

- `CLOUDERA_HMS_BASE_IMAGE`
- `CLOUDERA_HMS_IMAGE`
- `CLOUDERA_HMS_IMAGE_TAGS`
- `CLOUDERA_HMS_IMAGE_VARIANT`

Variant tasks are also available:

- `:image:jibAll` pushes PostgreSQL and MariaDB variants.
- `:image:jibDockerBuildAll` builds PostgreSQL and MariaDB variants into Docker.
- `:image:jibPostgres`, `:image:jibDockerBuildPostgres`, and `:image:jibBuildTarPostgres` build only the PostgreSQL variant.
- `:image:jibMariadb`, `:image:jibDockerBuildMariadb`, and `:image:jibBuildTarMariadb` build only the MariaDB variant.

Runtime configuration is environment-variable driven. The image supports:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`
- `MARIADB_DATABASE`
- `MARIADB_USER`
- `MARIADB_PASSWORD`
- `MARIADB_PORT`
- `MARIADB_RANDOM_ROOT_PASSWORD`
- `HMS_DATABASE_TYPE`
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
