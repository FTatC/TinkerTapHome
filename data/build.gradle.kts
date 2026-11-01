plugins {
    id("tinkertap.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.ftatc.tinkertaphome.data"
}

room {
    // Schemat exporteras och checkas in, så att migreringar kan skrivas och testas (se CLAUDE.md).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(testFixtures(project(":domain")))

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.turbine)
}
