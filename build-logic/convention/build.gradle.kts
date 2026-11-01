plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tinkertap.android.application"
            implementationClass = "io.github.ftatc.tinkertaphome.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "tinkertap.android.library"
            implementationClass = "io.github.ftatc.tinkertaphome.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "tinkertap.jvm.library"
            implementationClass = "io.github.ftatc.tinkertaphome.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
