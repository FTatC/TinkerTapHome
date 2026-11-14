package io.github.ftatc.tinkertaphome.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Provar varje regel mot exempel som bryter mot den, så att en regel inte kan passera för att den
 * aldrig hittar något. Exemplen ligger i `src/test/resources/violations/` och kompileras inte.
 */
class ArchitectureRulesTest {

    // Konsist vill ha sökvägen relativt projektets rot.
    private fun example(name: String): KoScope =
        Konsist.scopeFromFile("architecture/src/test/resources/violations/$name")

    @Test
    fun `finds top-level declarations that are visible outside the module`() {
        assertEquals(
            setOf("PublicClass", "PublicInterface", "PublicObject", "publicFunction", "publicProperty", "PublicAlias"),
            visibleOutsideModule(example("PublicDeclarations.kt")).names(),
        )
    }

    @Test
    fun `finds Kotlin Result in public APIs but not in own result types or private helpers`() {
        assertEquals(
            setOf("readValue", "observeValues", "takeResult", "lastResult"),
            resultInApi(example("ResultApi.kt")).names(),
        )
    }

    @Test
    fun `finds mutable state in objects and at top level but not in classes`() {
        assertEquals(
            setOf("count", "items", "state", "topLevelCount"),
            mutableGlobalState(example("MutableState.kt")).names(),
        )
    }

    private fun List<Violation>.names(): Set<String> = map { it.name }.toSet()
}
