plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    mainClass.set("com.lightchat.server.MainKt")
}

group = "com.lightchat.server"
version = "1.0.0"

dependencies {
    implementation("io.netty:netty-all:4.1.109.Final")
    implementation("org.json:json:20231013")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.5")

    testImplementation("junit:junit:4.13.2")
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
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
