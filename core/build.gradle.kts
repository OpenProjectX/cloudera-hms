plugins {
    id("buildsrc.convention.kotlin-jvm")
}

configurations.configureEach {
    exclude(group = "org.apache.zookeeper")
    exclude(group = "org.apache.hadoop" , module = "hadoop-yarn-server-resourcemanager")
    exclude(group = "org.eclipse.jetty")
    exclude(group = "org.apache.tez")
    exclude(group = "org.apache.curator")
    exclude(group = "org.apache.twill")




}

dependencies {
    implementation(libs.clouderaHiveStandaloneMetastore)
    implementation(libs.clouderaHiveMetastore)
    implementation(libs.clouderaHadoopCommon)
    implementation(libs.postgresql)

    testImplementation(project(":junit5"))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
