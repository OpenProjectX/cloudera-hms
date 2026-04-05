plugins {
    id("buildsrc.convention.kotlin-jvm")
}

configurations.configureEach {
    exclude(group = "org.apache.parquet", module = "parquet-hadoop-bundle")
}

dependencies {
    testImplementation(project(":core")) {
        exclude("org.apache.parquet", module = "parquet-hadoop-bundle")
    }
    testImplementation(project(":junit5"))
    testImplementation(libs.bundles.clouderaSpark) {
//        exclude(group = "org.apache.parquet")
    }
    testImplementation(libs.clouderaHadoopAws)
//    testImplementation(libs.icebergSparkRuntime)
    testImplementation(libs.bundles.spark.iceberg) {
//        exclude(group = "org.apache.parquet")
    }
//    testImplementation(libs.parquet)


    testImplementation(libs.awsSdkS3)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
