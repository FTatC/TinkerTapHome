package io.github.ftatc.tinkertaphome.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Projektets version catalog (`gradle/libs.versions.toml`). */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

internal val Project.javaTarget: JavaVersion
    get() = JavaVersion.toVersion(libs.findVersion("jvmTarget").get().requiredVersion)

/** Testberoenden som alla moduler använder för JVM-enhetstester. */
internal fun Project.addUnitTestDependencies() {
    dependencies {
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        add("testImplementation", libs.findLibrary("turbine").get())
    }
}

/** Testberoenden som alla Android-moduler använder för instrumenterade tester. */
internal fun Project.addAndroidTestDependencies() {
    dependencies {
        add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
        add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
    }
}

internal const val TEST_INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
