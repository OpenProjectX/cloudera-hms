import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
}

val shadedPrefix = "org.openprojectx.cloudera.hms.runtime.shaded"

configurations.configureEach {
    exclude(group = "org.apache.zookeeper")
    exclude(group = "org.apache.hadoop", module = "hadoop-yarn-server-resourcemanager")
    exclude(group = "org.eclipse.jetty")
    exclude(group = "org.apache.tez")
    exclude(group = "org.apache.curator")
    exclude(group = "org.apache.twill")
    exclude(group = "org.apache.parquet", module = "parquet-hadoop-bundle")

    listOf(
        "org.apache.atlas",
        "com.sun.jersey",
        "org.apache.calcite.avatica",
        "org.apache.calcite",
        "junit",
        "co.cask.tephra",
        "org.apache.ivy",
        "org.apache.ant",
        "org.codehaus.groovy",
        "javax.servlet",
        "commons-cli",
        "org.apache.orc",
    )
        .forEach { exclude(group = it) }

//    listOf(
//        "org.apache.hive" to "hive-llap-tez",
//        "org.apache.hadoop" to "hadoop-yarn-registry",
//        "org.apache.hadoop" to "hadoop-yarn-api",
//        "org.apache.hive" to "hive-vector-code-gen",
//        "org.apache.hive" to "hive-shims",
//    )
//        .forEach { (group, module) ->
//            exclude(group = group, module = module)
//        }

}

dependencies {
    implementation(project(":core")) {

    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
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
    listOf(
        "com.google.common",
        "com.google.gson",
        "org.codehaus.jackson",
        "org.apache.thrift",
        "org.apache.zookeeper",
        "org.apache.parquet",
        "org.apache.hive",
        "org.apache.hadoop",
        "org.apache.avro",
        "org.apache.parquet",
        "com.fasterxml.jackson",
        "io.netty",
        "org.apache.commons",
        "org.apache.http"
    )
        .forEach {
            relocate(it, "$shadedPrefix.$it")
        }


}

publishing {
    publications.named<MavenPublication>("mavenJava") {
        setArtifacts(emptyList<Any>())
        artifact(tasks.shadowJar)
        artifact(tasks.sourcesJar)
        artifact(tasks.javadocJar)
    }
}
