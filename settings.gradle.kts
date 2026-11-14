pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Pluginportalen får bara leverera det som inte finns hos Google eller Maven Central.
        // Annars kan ett paket med samma namn som ett riktigt beroende hämtas därifrån.
        gradlePluginPortal {
            content {
                includeGroupByRegex("org\\.gradle\\.toolchains.*")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TinkerTapHome"

include(":app")
include(":domain")
include(":data")
include(":architecture")
