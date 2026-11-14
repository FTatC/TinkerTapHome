// Arkitekturtester med Konsist (steg 7.2 i docs/roadmap.md). Modulen har bara tester och läser
// källkoden i de andra modulerna. Den beror inte på dem, så inget av appens kod hamnar här.
plugins {
    id("tinkertap.jvm.library")
}

dependencies {
    testImplementation(libs.konsist)
}
