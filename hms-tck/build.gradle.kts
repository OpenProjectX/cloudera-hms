import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.named

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

configurations.configureEach {
    exclude(group = "org.apache.parquet", module = "parquet-hadoop-bundle")
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "org.apache.hadoop", module = "hadoop-yarn-server-resourcemanager")
    exclude(group = "org.eclipse.jetty")
    exclude(group = "org.apache.tez")
    exclude(group = "org.apache.curator")
    exclude(group = "org.apache.twill")
    resolutionStrategy.force("org.slf4j:slf4j-api:1.7.36")
}

val sourceSets = extensions.getByType<SourceSetContainer>()

val coreTck: SourceSet = sourceSets.create("coreTck") {
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

val runtimeTck: SourceSet = sourceSets.create("runtimeTck") {
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

dependencies {
    api(libs.junitJupiterApi)
    api(libs.clouderaHiveMetastore)

    implementation(libs.testcontainersPostgresql)

    add("${coreTck.name}Implementation", sourceSets.named("main").get().output)
    add("${coreTck.name}RuntimeOnly", project(":core"))

    add("${runtimeTck.name}Implementation", sourceSets.named("main").get().output)
    add(
        "${runtimeTck.name}RuntimeOnly",
        project(mapOf("path" to ":runtime", "configuration" to "shadedRuntimeElements"))
    )

    listOf(coreTck, runtimeTck).forEach { sourceSet ->
        add("${sourceSet.name}Implementation", libs.junitJupiterApi)
        add("${sourceSet.name}Implementation", libs.junitJupiterEngine)
        add("${sourceSet.name}Implementation", libs.testcontainersPostgresql)
        add("${sourceSet.name}Implementation", libs.clouderaHiveMetastore)
        add("${sourceSet.name}RuntimeOnly", libs.junitPlatformLauncher)
    }
}

val coreTckTest = tasks.register<Test>("coreTckTest") {
    description = "Runs the HMS TCK against the unshaded core classpath."
    group = "verification"
    testClassesDirs = coreTck.output.classesDirs
    classpath = coreTck.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform()
}

val runtimeTckTest = tasks.register<Test>("runtimeTckTest") {
    description = "Runs the HMS TCK against the shaded runtime artifact and its external runtime deps."
    group = "verification"
    dependsOn(":runtime:shadowJar")
    testClassesDirs = runtimeTck.output.classesDirs
    classpath = runtimeTck.runtimeClasspath
    shouldRunAfter(coreTckTest)
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    dependsOn(coreTckTest, runtimeTckTest)
    enabled = false
}
