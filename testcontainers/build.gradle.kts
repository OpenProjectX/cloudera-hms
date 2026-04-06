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

    testImplementation(project(":hms-tck-core"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation(libs.testcontainersJunit)
    testImplementation(libs.clouderaHiveMetastore)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.named<Test>("test") {
    dependsOn(":image:jibDockerBuild")
    useJUnitPlatform()
}
