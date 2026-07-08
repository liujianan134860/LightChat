plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val lightChatApiUrl = providers.gradleProperty("LIGHTCHAT_API_URL").orNull
    ?: System.getenv("LIGHTCHAT_API_URL")
    ?: "http://10.0.2.2:8081"
val lightChatWsUrl = providers.gradleProperty("LIGHTCHAT_WS_URL").orNull
    ?: System.getenv("LIGHTCHAT_WS_URL")
    ?: "ws://10.0.2.2:8080/ws"

android {
    namespace = "com.lightchat.core.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "LIGHTCHAT_API_URL", lightChatApiUrl.asBuildConfigString())
        buildConfigField("String", "LIGHTCHAT_WS_URL", lightChatWsUrl.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":shared:protocol"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

kapt {
    correctErrorTypes = true
}
