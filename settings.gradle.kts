pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LightChat"
include(":app")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:domain")
include(":core:designsystem")
include(":feature:auth")
include(":shared:protocol")
include(":server")
