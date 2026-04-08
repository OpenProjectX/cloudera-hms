import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.jib)
    application
}

val defaultImage = "ghcr.io/openprojectx/cloudera-hms"
val imageName = System.getenv("CLOUDERA_HMS_IMAGE")
    ?.takeIf(String::isNotBlank)
    ?: defaultImage
val baseImage = System.getenv("CLOUDERA_HMS_BASE_IMAGE")
    ?.takeIf(String::isNotBlank)
val unresolvedBaseImageFallback = "docker://ghcr.io/openprojectx/postgres14-jdk17:latest"
val imageTags = System.getenv("CLOUDERA_HMS_IMAGE_TAGS")
    ?.split(",")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()
    ?: setOf(project.version.toString())


dependencies {
    implementation(project(":core")) {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
    }
}

application {
    mainClass.set("org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt")
}

tasks.withType<Test>().configureEach {
    enabled = false
}

tasks.matching { it.name in setOf("jar", "sourcesJar", "javadocJar") }.configureEach {
    enabled = false
}

jib {
    from {
        image = baseImage ?: unresolvedBaseImageFallback
    }
    to {
        image = imageName
        tags = imageTags
    }
    container {
        mainClass = "org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt"
        appRoot = "/opt/cloudera-hms"
        workingDirectory = "/opt/cloudera-hms"
        creationTime = "USE_CURRENT_TIMESTAMP"
        entrypoint = listOf("/bin/bash", "/opt/cloudera-hms/bin/entrypoint.sh")
        ports = listOf("5432", "9083")
        environment = mapOf(
            "POSTGRES_DB" to "metastore_db",
            "POSTGRES_USER" to "hive",
            "POSTGRES_PASSWORD" to "hive-password",
            "POSTGRES_PORT" to "5432",
            "HMS_HOST" to "0.0.0.0",
            "HMS_PORT" to "9083",
            "HMS_WAREHOUSE_DIR" to "/var/lib/cloudera-hms/warehouse",
            "HMS_SCHEMA_RESOURCE" to "/hive-schema-3.1.3000.postgres.sql",
            "HMS_INITIALIZE_SCHEMA" to "true",
            "HMS_LOG_LEVEL" to "INFO",
        )
    }
    extraDirectories {
        paths {
            path {
                setFrom(file("src/main/jib"))
                into = "/"
            }
        }
        permissions = mapOf(
            "/opt/cloudera-hms/bin/entrypoint.sh" to "755"
        )
    }
}

listOf("jib", "jibBuildTar", "jibDockerBuild").forEach { taskName ->
    tasks.named(taskName) {
        notCompatibleWithConfigurationCache(
            "Jib image tasks in :image are unstable when restored from Gradle configuration cache in this build."
        )
    }
}
