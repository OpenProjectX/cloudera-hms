import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.jib)
    application
}

data class HmsImageVariant(
    val name: String,
    val databaseType: String,
    val baseImage: String,
    val tagSuffix: String?,
    val schemaResource: String,
    val warehouseDir: String,
)

val imageVariants = listOf(
    HmsImageVariant(
        name = "postgres",
        databaseType = "postgresql",
        baseImage = "ghcr.io/openprojectx/postgres14-jdk17:latest",
        tagSuffix = null,
        schemaResource = "/hive-schema-3.1.3000.postgres.sql",
        warehouseDir = "/var/lib/cloudera-hms/warehouse",
    ),
    HmsImageVariant(
        name = "mariadb",
        databaseType = "mariadb",
        baseImage = "ghcr.io/openprojectx/mariadb10.6-jdk17:latest",
        tagSuffix = "mariadb",
        schemaResource = "/hive-schema-3.1.3000.mysql.sql",
        warehouseDir = "/var/lib/mysql/cloudera_hms/warehouse",
    ),
)

fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

val defaultImage = "ghcr.io/openprojectx/cloudera-hms"
val imageName = System.getenv("CLOUDERA_HMS_IMAGE")
    ?.takeIf(String::isNotBlank)
    ?: defaultImage
val imageVariantName = providers.gradleProperty("clouderaHmsImageVariant")
    .orElse(providers.environmentVariable("CLOUDERA_HMS_IMAGE_VARIANT"))
    .orElse("postgres")
    .get()
val imageVariant = imageVariants.singleOrNull { it.name == imageVariantName }
    ?: error("Unsupported clouderaHmsImageVariant '$imageVariantName'. Supported values: ${imageVariants.joinToString { it.name }}")
val baseImage = providers.gradleProperty("clouderaHmsBaseImage")
    .orElse(providers.environmentVariable("CLOUDERA_HMS_BASE_IMAGE"))
    .orNull
    ?.takeIf(String::isNotBlank)
val unresolvedBaseImageFallback = "docker://${imageVariant.baseImage}"
val imageTags = System.getenv("CLOUDERA_HMS_IMAGE_TAGS")
    ?.split(",")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: listOf(project.version.toString())
val variantImageTags = imageTags
    .map { tag -> imageVariant.tagSuffix?.let { "$tag-$it" } ?: tag }
    .toSet()


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
        tags = variantImageTags
    }
    container {
        mainClass = "org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt"
        appRoot = "/opt/cloudera-hms"
        workingDirectory = "/opt/cloudera-hms"
        creationTime = "USE_CURRENT_TIMESTAMP"
        entrypoint = listOf("/bin/bash", "/opt/cloudera-hms/bin/entrypoint.sh")
        ports = listOf("5432", "3306", "9083")
        environment = mapOf(
            "HMS_DATABASE_TYPE" to imageVariant.databaseType,
            "POSTGRES_DB" to "metastore_db",
            "POSTGRES_USER" to "hive",
            "POSTGRES_PASSWORD" to "hive-password",
            "POSTGRES_PORT" to "5432",
            "HMS_HOST" to "0.0.0.0",
            "HMS_PORT" to "9083",
            "HMS_WAREHOUSE_DIR" to imageVariant.warehouseDir,
            "HMS_SCHEMA_RESOURCE" to imageVariant.schemaResource,
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

fun registerVariantBuildTasks(jibTaskName: String, registerAggregate: Boolean = true) {
    val aggregateTaskName = "${jibTaskName}All"
    val variantTaskNames = imageVariants.map { variant ->
        val taskName = "${jibTaskName}${variant.name.capitalized()}"
        if (jibTaskName == "jib") {
            tasks.register<Exec>(taskName) {
                group = "containerization"
                description = "Runs :image:$jibTaskName with the ${variant.name} database base image."
                workingDir = rootProject.projectDir
                commandLine(
                    rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                    "--no-configuration-cache",
                    ":image:$jibTaskName",
                    "-PclouderaHmsImageVariant=${variant.name}",
                    "-PclouderaHmsBaseImage=${variant.baseImage}",
                    "-Djib.serialize=true",
                    "-Djib.disableUpdateChecks=true",
                )
            }
        } else {
            tasks.register<GradleBuild>(taskName) {
                group = "containerization"
                description = "Runs :image:$jibTaskName with the ${variant.name} database base image."
                dir = rootProject.projectDir
                tasks = listOf(":image:$jibTaskName")
                startParameter.projectProperties = gradle.startParameter.projectProperties + mapOf(
                    "clouderaHmsImageVariant" to variant.name,
                    "clouderaHmsBaseImage" to variant.baseImage,
                )
            }
        }
        taskName
    }
    variantTaskNames.zipWithNext().forEach { (first, second) ->
        tasks.named(second) {
            mustRunAfter(first)
        }
    }

    if (registerAggregate) {
        tasks.register(aggregateTaskName) {
            group = "containerization"
            description = "Runs :image:$jibTaskName for all database image variants."
            dependsOn(variantTaskNames)
        }
    }
}

registerVariantBuildTasks("jib")
registerVariantBuildTasks("jibBuildTar", registerAggregate = false)
registerVariantBuildTasks("jibDockerBuild")
