pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://clojars.org/repo/")
        maven("https://maven.mozilla.org/maven2/")
    }
}
rootProject.name = "AirSky Browser"
include(":app")