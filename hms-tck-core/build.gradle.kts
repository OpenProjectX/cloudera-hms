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
    api(libs.clouderaHiveMetastore)
    api("org.junit.jupiter:junit-jupiter-api:5.11.4")
}
