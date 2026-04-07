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

    resolutionStrategy.eachDependency {
        if (requested.group == "org.scala-lang") {
            val scalaVersion = libs.versions.scalaFull.get()
            useVersion(scalaVersion)
            because("standardize on Scala $scalaVersion for binary compatibility with spark ${libs.versions.clouderaSpark.get()}")
        }
    }
}

dependencies {
    testImplementation(platform(libs.junitBom))
    testImplementation(project(":core")) {
        exclude("org.apache.parquet", module = "parquet-hadoop-bundle")
    }
    testImplementation(project(":junit5"))
    testImplementation(libs.bundles.clouderaSpark) {
//        exclude(group = "org.apache.parquet")
    }
    testImplementation(libs.clouderaHadoopAws)
    testImplementation(libs.bundles.spark.iceberg.runtime)
//    testImplementation(libs.bundles.spark.iceberg) {
////        exclude(group = "org.apache.parquet")
//    }
//    testImplementation(libs.parquet)


//    testImplementation(libs.awsSdkS3)
    testImplementation(libs.iceberg.aws.bundle)
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiterEngine)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
