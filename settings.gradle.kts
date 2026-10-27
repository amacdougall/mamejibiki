pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // AnkiDroid's API library (Send to AnkiDroid) is published only on JitPack.
        maven("https://jitpack.io") { content { includeGroup("com.github.ankidroid") } }
    }
}
rootProject.name = "mamejibiki"
include(":app")
