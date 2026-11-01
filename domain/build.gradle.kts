import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Ren Kotlin/JVM-modul: inga Android-beroenden får finnas här (se CLAUDE.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    val target = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    sourceCompatibility = target
    targetCompatibility = target
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
