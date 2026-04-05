plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.junitJupiterApi)
    implementation(libs.testcontainersPostgresql)
}
