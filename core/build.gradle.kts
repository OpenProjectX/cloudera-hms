plugins {
    id("buildsrc.convention.kotlin-jvm")
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
