plugins {
    id("buildsrc.convention.kotlin-jvm")
}

configurations.configureEach {
    exclude(group = "org.apache.parquet", module = "parquet-hadoop-bundle")
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "org.apache.hadoop" , module = "hadoop-yarn-server-resourcemanager")
    exclude(group = "org.eclipse.jetty")
    exclude(group = "org.apache.tez")
    exclude(group = "org.apache.curator")
    exclude(group = "org.apache.twill")
    resolutionStrategy.force("org.slf4j:slf4j-api:1.7.36")
}

//repositories {
//    mavenLocal()
//}

dependencies {
    testImplementation(project(":core")) {
        exclude("org.apache.parquet", module = "parquet-hadoop-bundle")
    }
//    testImplementation("org.openprojectx.cloudera.hms:runtime:0.1.2")
    testImplementation(project(":junit5"))

    testImplementation(libs.clouderaHiveMetastore)
//    testImplementation(libs.clouderaHadoopCommon)
//    testImplementation(libs.clouderaHiveStandaloneMetastore)


    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
