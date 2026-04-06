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
    api(platform(libs.junitBom))
    api(libs.clouderaHiveMetastore)
    api(libs.junitJupiterApi)
}
