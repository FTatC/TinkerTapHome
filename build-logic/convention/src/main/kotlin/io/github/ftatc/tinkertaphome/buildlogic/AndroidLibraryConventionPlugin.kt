package io.github.ftatc.tinkertaphome.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Gemensam konfiguration för Android-biblioteksmoduler: SDK-nivåer, Java-version, testkörare och
 * testberoenden. Namespace och modulspecifika beroenden sätts i modulen.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(libs.intVersion("compileSdk"))
            }
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                testInstrumentationRunner = TEST_INSTRUMENTATION_RUNNER
            }
            compileOptions {
                sourceCompatibility = javaTarget
                targetCompatibility = javaTarget
            }
        }

        addUnitTestDependencies()
        addAndroidTestDependencies()
    }
}
