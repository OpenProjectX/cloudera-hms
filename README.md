# cloudera-hms

Standalone Cloudera-compatible Hive Metastore for local development, implemented in Kotlin and aligned to the Cloudera Spark stack.

The current dependency baseline is driven from Cloudera Spark `3.3.0.3.3.7180.2-4`, with the resolved Hive and Hadoop coordinates pinned in the Gradle version catalog.

## Modules

- `core`: metastore runtime, PostgreSQL schema bootstrap, configuration helpers, and Hive client TCK.
- `junit5`: JUnit 5 extension that provisions PostgreSQL and starts the metastore for tests.
- `spark`: Spark-facing TCK that validates Spark SQL against the metastore.

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
- exposes a small Kotlin API for starting the metastore in a dedicated JVM process

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

## Tests

Implemented test coverage:

- Hive metastore client TCK in [HiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/core/src/test/kotlin/org/openprojectx/cloudera/hms/core/HiveMetastoreClientTckTest.kt)
- Spark integration TCK in [SparkHiveMetastoreTckTest.kt](/data/Git/cloudera-hms/spark/src/test/kotlin/org/openprojectx/cloudera/hms/spark/SparkHiveMetastoreTckTest.kt)
- reusable JUnit 5 extension in [ClouderaHiveMetastoreExtension.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreExtension.kt)

Useful commands:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:compileTestKotlin
```

## Verification status

Verified in this workspace:

- `GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:compileTestKotlin`

Not fully executable in this workspace:

- `GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test`

The test code compiles, but Testcontainers cannot start because the local Docker environment is rejecting the API version negotiated by the client. If Docker/Testcontainers is fixed on the host, the core and Spark suites are the intended end-to-end validation path.
