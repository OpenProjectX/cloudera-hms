# Contributing

## Gradle

Use the project wrapper from the repository root:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew <task>
```

This repository is configured around `GRADLE_USER_HOME=/data/.gradle`. Do not switch to `/tmp` unless there is a concrete reason.

## Version alignment

The main stack versions are declared in [gradle/libs.versions.toml](/data/Git/cloudera-hms/gradle/libs.versions.toml):

- Cloudera Spark: `3.3.2.3.3.7190.9-1`
- Cloudera Hive: `3.1.3000.7.1.9.14-2`
- Cloudera Hadoop: `3.1.1.7.1.8.0-801`

If you need to realign the stack to a different Cloudera Spark release, update the catalog and re-check the resolved dependency graph with:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:dependencies --configuration testRuntimeClasspath
```

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

## Runtime notes

The standalone shaded runtime now lives in `runtime`, not `core`.

The `runtime` module:

- depends on `:core`
- produces a shaded runtime jar
- relocates selected third-party packages to reduce conflict risk with host applications
- leaves DataNucleus JDO jars unshaded and published as external runtime dependencies

For modules inside this repository, you do not need to publish `runtime` first. The module exposes a consumable `shadedRuntimeElements` configuration so sibling projects can depend on the shaded jar directly:

```kotlin
dependencies {
    runtimeOnly(project(mapOf("path" to ":runtime", "configuration" to "shadedRuntimeElements")))
}
```

## Container image development

The `image` module builds a runnable container image with Jib. The image expects a base image that already includes:

- PostgreSQL
- JDK 17
- the standard PostgreSQL container entrypoint at `/usr/local/bin/docker-entrypoint.sh`

Build configuration is environment-variable driven:

- `CLOUDERA_HMS_BASE_IMAGE`
- `CLOUDERA_HMS_IMAGE`
- `CLOUDERA_HMS_IMAGE_TAGS`

Useful commands:

```bash
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibBuildTar
CLOUDERA_HMS_BASE_IMAGE=your-registry/postgres-jdk17:tag GRADLE_USER_HOME=/data/.gradle ./gradlew :image:jibDockerBuild
```

The Jib tasks in `:image` are marked incompatible with Gradle configuration cache because that path has been unstable in this build.

## Tests

Useful commands:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :core:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :hms-tck:coreTckTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :hms-tck:runtimeTckTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :runtime:shadowJar
GRADLE_USER_HOME=/data/.gradle ./gradlew :image:compileKotlin :testcontainers:compileKotlin :testcontainers:compileTestKotlin
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:test
GRADLE_USER_HOME=/data/.gradle ./gradlew :spark:compileTestKotlin
```

Implemented coverage includes:

- reusable Hive metastore TCK support in [ReusableHiveMetastoreClientTck.kt](/data/Git/cloudera-hms/hms-tck-core/src/main/kotlin/org/openprojectx/cloudera/hms/tck/ReusableHiveMetastoreClientTck.kt)
- internal core-classpath TCK execution in [CoreClasspathHiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/hms-tck/src/coreTck/kotlin/org/openprojectx/cloudera/hms/tck/CoreClasspathHiveMetastoreClientTckTest.kt)
- internal shaded-runtime TCK execution in [RuntimeShadedHiveMetastoreClientTckTest.kt](/data/Git/cloudera-hms/hms-tck/src/runtimeTck/kotlin/org/openprojectx/cloudera/hms/tck/RuntimeShadedHiveMetastoreClientTckTest.kt)
- container-backed TCK execution in [ClouderaHiveMetastoreContainerTckTest.kt](/data/Git/cloudera-hms/testcontainers/src/test/kotlin/org/openprojectx/cloudera/hms/testcontainers/ClouderaHiveMetastoreContainerTckTest.kt)

## Verification status

Verified in this workspace:

- `GRADLE_USER_HOME=/data/.gradle ./gradlew :core:compileKotlin :spark:compileTestKotlin`
- `GRADLE_USER_HOME=/data/.gradle ./gradlew :junit5:compileKotlin :core:compileTestKotlin :spark:compileTestKotlin`
- `GRADLE_USER_HOME=/data/.gradle ./gradlew --no-configuration-cache :hms-tck-core:compileKotlin :hms-tck:compileKotlin :testcontainers:compileTestKotlin`

Not fully executable in this workspace:

- container-backed tests that require a working local Docker/Testcontainers setup
- full image publication flows that require registry credentials and a reachable registry
