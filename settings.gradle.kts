pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "compose-spike"

include(":cupcake")
project(":cupcake").projectDir = file("apps/cupcake")
