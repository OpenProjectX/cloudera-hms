plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
//    implementation("org.apache.hive:hive-metastore:3.1.3")
//    // Source: https://mvnrepository.com/artifact/org.apache.hive/hive-standalone-metastore
//    testImplementation("org.apache.hive:hive-standalone-metastore:3.1.3")
//    implementation("org.apache.hadoop:hadoop-client:3.1.0")

    // Source: https://mvnrepository.com/artifact/org.apache.spark/spark-sql
    implementation("org.apache.spark:spark-sql_2.13:3.3.2")
    implementation("org.apache.spark:spark-core_2.13:3.3.2")
    implementation("org.apache.spark:spark-hive_2.13:3.3.2")



    // Source: https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-engine
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}