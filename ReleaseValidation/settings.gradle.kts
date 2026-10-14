pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("../attributehq/build/validation-repository") }
        google()
        mavenCentral()
    }
}
rootProject.name = "AttributeHQAndroidConsumer"
include(":app")
