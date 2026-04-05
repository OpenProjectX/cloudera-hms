# cloudera-hms

Standalone Cloudera-compatible Hive Metastore for local development, implemented in Kotlin and aligned to the Cloudera Spark stack.

The current dependency baseline is driven from Cloudera Spark `3.3.0.3.3.7180.2-4`, with the resolved Hive and Hadoop coordinates pinned in the Gradle version catalog.

## Modules

- `core`: metastore runtime, PostgreSQL schema bootstrap, configuration helpers, and Hive client TCK.
- `junit5`: annotation-driven JUnit 5 support that provisions PostgreSQL and starts the metastore for tests.
- `runtime`: shaded standalone runtime jar for launching the metastore with relocated third-party dependencies.
- `spark`: Spark-facing TCKs that validate Spark SQL and Iceberg against the metastore.

## Version alignment

The main stack versions are declared in [gradle/libs.versions.toml](/data/Git/cloudera-hms/gradle/libs.versions.toml):

- Cloudera Spark: `3.3.2.3.3.7190.9-1`
- Cloudera Hive: `3.1.3000.7.1.9.14-2`
- Cloudera Hadoop: `3.1.1.7.1.8.0-801`

If you need to realign the stack to a different Cloudera Spark release, update the catalog and re-check the resolved dependency graph with:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:dependencies --configuration testRuntimeClasspath
```

Always use `GRADLE_USER_HOME=/data/.gradle` for Gradle commands in this repo.

## Local development database

A PostgreSQL 14 compose file is provided for local development:

```bash
docker compose up -d
```

This starts:

- PostgreSQL on `localhost:5432`
- database `metastore_db`
- user `hive`
- password `hive-password`

See [docker-compose.yml](/data/Git/cloudera-hms/docker-compose.yml).

## Runtime behavior

The metastore runtime in `core`:

- uses PostgreSQL as the backing store
- initializes schema from the bundled `core/src/main/resources/hive-schema-3.1.3000.postgres.sql` by default
- allows overriding the schema with a custom SQL file
- accepts additional HMS-side Hadoop or Hive configuration entries such as `fs.s3a.*`
- can generate a simple Log4j 2 configuration with configurable root log level
- exposes a small Kotlin API for starting the metastore in a dedicated JVM process

The standalone shaded runtime now lives in `runtime`, not `core`.

Breaking change:

- use `:runtime:shadowJar` for the fat runtime jar instead of `:core:shadowJar`

The `runtime` module:

- depends on `:core`
- produces a shaded `-all` jar
- relocates selected third-party packages to reduce conflict risk with host applications
- keeps the exclusions already declared in `core` as exclusions rather than relocating them

Default configuration is defined in [ClouderaHiveMetastoreConfig.kt](/data/Git/cloudera-hms/core/src/main/kotlin/org/openprojectx/cloudera/hms/core/ClouderaHiveMetastoreConfig.kt).

Server bootstrap happens in [HiveMetastoreServerMain.kt](core/src/main/kotlin/org/openprojectx/cloudera/hms/core/HiveMetastoreServerMain.kt).

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
- `logLevel` for generated HMS server logging such as `DEBUG` or `TRACE`
- `logConfigFile` if you want to supply a complete custom Log4j 2 properties file instead of the generated one

This matters for S3-backed catalogs: Spark-side `fs.s3a.*` settings are not enough on their own. The HMS process must also receive matching S3A settings if it needs to create or validate `s3a://...` warehouse or namespace paths.

## JUnit 5 usage

The `junit5` module now uses a custom annotation instead of requiring direct `@ExtendWith(...)` usage.

Use [ClouderaHiveMetastoreTest.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreTest.kt):

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

Breaking change:

- tests should now use `@ClouderaHiveMetastoreTest` instead of `@ExtendWith(ClouderaHiveMetastoreExtension::class)`

## Test logging

The Spark test suite uses [log4j2.xml](/data/Git/cloudera-hms/spark/src/test/resources/log4j2.xml).

The `spark` module excludes `org.slf4j:slf4j-reload4j` so Spark test logging stays on the Log4j 2 stack already brought in by Spark and Hive. It also forces `org.slf4j:slf4j-api:1.7.36` because the Spark 3.3 and Hive logging bridge in this stack still targets the SLF4J 1.7 API. If you customize Spark test logging, update `log4j2.xml` rather than adding Log4j 1 style configuration.

## Tests

Implemented test coverage:

- Hive metastore client TCK in [HiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/core/src/test/kotlin/org/openprojectx/cloudera/hms/core/HiveMetastoreClientTckTest.kt)
- Spark integration TCK in [SparkHiveMetastoreTckTest.kt](/data/Git/cloudera-hms/spark/src/test/kotlin/org/openprojectx/cloudera/hms/spark/SparkHiveMetastoreTckTest.kt)
- Spark Iceberg over S3 TCK using LocalStack in [SparkIcebergS3TckTest.kt](/data/Git/cloudera-hms/spark/src/test/kotlin/org/openprojectx/cloudera/hms/spark/SparkIcebergS3TckTest.kt)
- annotation-driven JUnit 5 support in [ClouderaHiveMetastoreExtension.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreExtension.kt)

Useful commands:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :runtime:shadowJar
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:compileTestKotlin
```

## Verification status

Verified in this workspace:

- `GRADLE_USER_HOME=/data/.gradle ./gradlew :core:compileKotlin :spark:compileTestKotlin`
- `GRADLE_USER_HOME=/data/.gradle ./gradlew :junit5:compileKotlin :core:compileTestKotlin :spark:compileTestKotlin`

Not fully executable in this workspace:

- `GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test`

The test code compiles, but Testcontainers cannot start because the local Docker environment is rejecting the API version negotiated by the client. If Docker/Testcontainers is fixed on the host, the core and Spark suites are the intended end-to-end validation path.
