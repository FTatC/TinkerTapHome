package io.github.ftatc.tinkertaphome.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Gemensam konfiguration för appmodulen: SDK-nivåer, Java-version, testkörare och testberoenden.
 * Modulspecifika saker (namespace, applicationId, versionsnummer, Compose) sätts i modulen.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(libs.intVersion("compileSdk"))
            }
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
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
