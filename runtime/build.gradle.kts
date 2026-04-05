import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
}

val shadedPrefix = "org.openprojectx.cloudera.hms.runtime.shaded"

dependencies {
    implementation(project(":core"))
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    transform(Log4j2PluginsCacheFileTransformer::class.java)

    exclude("META-INF/DEPENDENCIES")
    exclude("META-INF/LICENSE")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE")
    exclude("META-INF/NOTICE.txt")
    exclude("META-INF/README.txt")
    exclude("META-INF/ASL2.0")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("git.properties")
    exclude("plugin.xml")
    exclude("META-INF/jersey-module-version")

    manifest {
        attributes["Main-Class"] = "org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt"
    }

    // Keep the core API package stable and relocate selected third-party libraries
    // that are most likely to conflict in embedding scenarios.
    relocate("com.google.common", "$shadedPrefix.com.google.common")
    relocate("com.google.gson", "$shadedPrefix.com.google.gson")
    relocate("org.codehaus.jackson", "$shadedPrefix.org.codehaus.jackson")
    relocate("org.apache.thrift", "$shadedPrefix.org.apache.thrift")
    relocate("org.apache.zookeeper", "$shadedPrefix.org.apache.zookeeper")
    relocate("org.apache.parquet", "$shadedPrefix.org.apache.parquet")
    relocate("org.apache.hive", "$shadedPrefix.org.apache.hive")
    relocate("org.apache.hadoop", "$shadedPrefix.org.apache.hadoop")
    relocate("org.apache.avro", "$shadedPrefix.org.apache.avro")
    relocate("org.apache.parquet", "$shadedPrefix.org.apache.parquet")



}
