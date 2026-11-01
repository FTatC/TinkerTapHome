// Ren Kotlin/JVM-modul: inga Android-beroenden får finnas här (se CLAUDE.md).
plugins {
    id("tinkertap.jvm.library")
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testFixturesApi(libs.kotlinx.coroutines.core)
}
