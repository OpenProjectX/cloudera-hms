# cloudera-hms

Standalone Cloudera-compatible Hive Metastore for local development, implemented in Kotlin and aligned to the Cloudera Spark stack.

The current dependency baseline is driven from Cloudera Spark `3.3.0.3.3.7180.2-4`, with the resolved Hive and Hadoop coordinates pinned in the Gradle version catalog.

## Modules

- `core`: metastore runtime, PostgreSQL schema bootstrap, configuration helpers, and Hive client TCK.
- `image`: Jib-based container image assembly for a combined PostgreSQL plus Hive metastore runtime.
- `junit5`: annotation-driven JUnit 5 support that provisions PostgreSQL and starts the metastore for tests.
- `hms-tck`: reusable Hive metastore TCK plus internal executions against both the `core` classpath and the shaded `runtime` artifact.
- `runtime`: shaded standalone runtime jar for launching the metastore with relocated third-party dependencies.
- `testcontainers`: JDK 11 Testcontainers wrapper for the built metastore image.
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
- leaves the DataNucleus JDO jars unshaded and published as normal transitive runtime dependencies, because their `plugin.xml` discovery does not survive jar shading safely
- those external DataNucleus/JDO jars are carried by the `runtime` module's published runtime metadata, not merged into the shaded jar
- does not relocate the Hive metastore or broad Hadoop packages, because the metastore's DataNucleus/JDO metadata expects those model classes at their original package names

Packaging note:

- the published `runtime` artifact is still the main entry point for embedding or launching HMS
- but the raw shaded jar alone is not intended to be copied in isolation anymore
- consumers should resolve `org.openprojectx.cloudera.hms:runtime` through Gradle or Maven so the external DataNucleus/JDO jars are also present at runtime

For modules inside this same repository, you do not need to publish `runtime` first. The module exposes a consumable `shadedRuntimeElements` configuration so sibling projects can depend on the shaded jar directly:

```kotlin
dependencies {
    runtimeOnly(project(mapOf("path" to ":runtime", "configuration" to "shadedRuntimeElements")))
}
```

## Container image

The `image` module builds a runnable container image with Jib. The image expects a base image that already includes:

- PostgreSQL
- JDK 17
- the standard PostgreSQL container entrypoint at `/usr/local/bin/docker-entrypoint.sh`

Build configuration is environment-variable driven:

- `CLOUDERA_HMS_BASE_IMAGE`: required base image reference
- `CLOUDERA_HMS_IMAGE`: target image name, defaults to `cloudera-hms:local`
- `CLOUDERA_HMS_IMAGE_TAGS`: optional comma-separated extra tags

Runtime configuration is also environment-variable driven. The image supports:

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

Useful commands:

```bash
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibBuildTar
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuild
```

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

## Testcontainers wrapper

The `testcontainers` module targets JDK 11 and wraps the image built by `:image`.

The main entry point is [ClouderaHiveMetastoreContainer.kt](/data/Git/cloudera-hms/testcontainers/src/main/kotlin/org/openprojectx/cloudera/hms/testcontainers/ClouderaHiveMetastoreContainer.kt). By default it uses `cloudera-hms:local`, or `CLOUDERA_HMS_TEST_IMAGE` when that variable is set.

Useful command:

```bash
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :testcontainers:test
```

Breaking change:

- tests should now use `@ClouderaHiveMetastoreTest` instead of `@ExtendWith(ClouderaHiveMetastoreExtension::class)`

## Test logging

The Spark test suite uses [log4j2.xml](/data/Git/cloudera-hms/spark/src/test/resources/log4j2.xml).

The `spark` module excludes `org.slf4j:slf4j-reload4j` so Spark test logging stays on the Log4j 2 stack already brought in by Spark and Hive. It also forces `org.slf4j:slf4j-api:1.7.36` because the Spark 3.3 and Hive logging bridge in this stack still targets the SLF4J 1.7 API. If you customize Spark test logging, update `log4j2.xml` rather than adding Log4j 1 style configuration.

## Tests

Implemented test coverage:

- reusable Hive metastore TCK support in [AbstractHiveMetastoreClientTck.kt](/data/Git/cloudera-hms/hms-tck/src/main/kotlin/org/openprojectx/cloudera/hms/tck/AbstractHiveMetastoreClientTck.kt)
- internal core-classpath TCK execution in [CoreClasspathHiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/hms-tck/src/coreTck/kotlin/org/openprojectx/cloudera/hms/tck/CoreClasspathHiveMetastoreClientTckTest.kt)
- internal shaded-runtime TCK execution in [RuntimeShadedHiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/hms-tck/src/runtimeTck/kotlin/org/openprojectx/cloudera/hms/tck/RuntimeShadedHiveMetastoreClientTckTest.kt)
- Hive metastore client TCK in [HiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/core/src/test/kotlin/org/openprojectx/cloudera/hms/core/HiveMetastoreClientTckTest.kt)
- Spark integration TCK in [SparkHiveMetastoreTckTest.kt](/data/Git/cloudera-hms/spark/src/test/kotlin/org/openprojectx/cloudera/hms/spark/SparkHiveMetastoreTckTest.kt)
- Spark Iceberg over S3 TCK using LocalStack in [SparkIcebergS3TckTest.kt](/data/Git/cloudera-hms/spark/src/test/kotlin/org/openprojectx/cloudera/hms/spark/SparkIcebergS3TckTest.kt)
- annotation-driven JUnit 5 support in [ClouderaHiveMetastoreExtension.kt](/data/Git/cloudera-hms/junit5/src/main/kotlin/org/openprojectx/cloudera/hms/junit5/ClouderaHiveMetastoreExtension.kt)

Useful commands:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :hms-tck:coreTckTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :hms-tck:runtimeTckTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :image:compileKotlin :testcontainers:compileKotlin :testcontainers:compileTestKotlin
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
