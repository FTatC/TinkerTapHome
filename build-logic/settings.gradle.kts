pluginManagement {
    repositories {
        mavenCentral()
        // Pluginportalen får bara leverera kotlin-dsl. Allt annat hämtas från Maven Central.
        gradlePluginPortal {
            content {
                includeGroupByRegex("org\\.gradle\\.kotlin.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
