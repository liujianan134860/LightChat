plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.lightchat.server.MainKt")
}

group = "com.lightchat.server"
version = "1.0.0"

dependencies {
    implementation(project(":shared:protocol"))
    implementation(libs.netty.all)
    implementation(libs.json)
    implementation(libs.mysql.connector)
    implementation(libs.aliyun.oss)

    testImplementation(libs.junit4)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.lightchat.server.MainKt"
    }
}
