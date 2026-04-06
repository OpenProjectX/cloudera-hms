import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.testcontainers)

    testImplementation(platform(libs.junitBom))
    testImplementation(project(":hms-tck-core"))
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.junitJupiterEngine)
    testImplementation(libs.testcontainersJunit)
    testImplementation(libs.clouderaHiveMetastore)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named<Test>("test") {
    dependsOn(":image:jibDockerBuild")
    useJUnitPlatform()
}
