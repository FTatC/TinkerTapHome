package io.github.ftatc.tinkertaphome.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kontrollerar appens egen kod mot reglerna i [ArchitectureRules]. */
class ArchitectureTest {

    private val data = Konsist.scopeFromProduction(moduleName = "data")
    private val domain = Konsist.scopeFromProduction(moduleName = "domain")
    private val allProduction = Konsist.scopeFromProduction()

    @Test
    fun `the scopes contain the code of the modules`() {
        // Ett felstavat modulnamn ger ett tomt scope, och då skulle alla regler passera av en slump.
        assertHasFiles(data, "data")
        assertHasFiles(domain, "domain")
        assertTrue(allProduction.files.size > data.files.size + domain.files.size)
    }

    @Test
    fun `every top-level declaration in data is internal`() {
        assertNoViolations("Gör deklarationen internal (se CLAUDE.md, :data).", visibleOutsideModule(data))
    }

    @Test
    fun `domain APIs do not use Kotlin Result`() {
        assertNoViolations("Använd Outcome eller en egen resultattyp (se CLAUDE.md, Fel är data).", resultInApi(domain))
    }

    @Test
    fun `production code has no mutable global state`() {
        assertNoViolations("Lägg tillståndet i en klass som injiceras via Hilt (se CLAUDE.md, Regler).", mutableGlobalState(allProduction))
    }

    private fun assertHasFiles(scope: KoScope, module: String) {
        assertTrue("Inga filer hittades i :$module", scope.files.isNotEmpty())
    }

    private fun assertNoViolations(advice: String, violations: List<Violation>) {
        assertEquals(advice, emptyList<String>(), violations.map { "${it.name} (${it.location})" })
    }
}
