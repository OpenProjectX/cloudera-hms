plugins {
    id("buildsrc.convention.kotlin-jvm")
}

configurations.configureEach {
//    exclude(group = "org.apache.zookeeper")
//    exclude(group = "org.apache.hadoop", module = "hadoop-yarn-server-resourcemanager")
//    exclude(group = "org.eclipse.jetty")
//    exclude(group = "org.apache.tez")
//    exclude(group = "org.apache.curator")
//    exclude(group = "org.apache.twill")
//    exclude(group = "org.apache.parquet", module = "parquet-hadoop-bundle")

//    listOf(
//        "org.apache.atlas",
//        "com.sun.jersey",
//        "org.apache.calcite.avatica",
//        "org.apache.calcite",
//        "junit",
//        "co.cask.tephra",
//        "org.apache.ivy",
//        "org.apache.ant",
//        "org.codehaus.groovy",
//        "javax.servlet",
//        "commons-cli",
//        "org.apache.orc",
//    )
//        .forEach { exclude(group = it) }

//    listOf(
//        "org.apache.hive" to "hive-llap-tez",
//        "org.apache.hadoop" to "hadoop-yarn-registry",
//        "org.apache.hadoop" to "hadoop-yarn-api",
//        "org.apache.hive" to "hive-vector-code-gen",
//        "org.apache.hive" to "hive-shims",
//
//
//        )
//        .forEach { (group, module) ->
//            exclude(group = group, module = module)
//        }

}

dependencies {
//    implementation(libs.clouderaHiveStandaloneMetastore)
//    implementation(libs.clouderaHiveMetastore)
//    implementation(libs.clouderaHadoopCommon)
    implementation(libs.bundles.clouderaHms) {
//        exclude("org.apache.hadoop", "hadoop-yarn-registry")
//        exclude("org.apache.calcite")
    }
    implementation(libs.clouderaHadoopAws)


    implementation(libs.postgresql)

//    testImplementation(project(":junit5"))

}
