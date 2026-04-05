import java.util.Properties

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":junit5"))
    testImplementation(libs.bundles.clouderaSpark)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
