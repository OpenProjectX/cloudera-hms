plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(platform(libs.junitBom))
    implementation(project(":core"))
    implementation(libs.junitJupiterApi)
    implementation(libs.testcontainersPostgresql)
}
