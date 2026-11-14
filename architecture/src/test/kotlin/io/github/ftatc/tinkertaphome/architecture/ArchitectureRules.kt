package io.github.ftatc.tinkertaphome.architecture

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

/**
 * Reglerna i CLAUDE.md som kompilatorn inte stoppar. Varje regel tar ett [KoScope] och returnerar
 * de deklarationer som bryter mot den, så att samma regel kan provas både mot appens kod och mot
 * exempel som bryter mot den.
 */

/** En deklaration som bryter mot en regel. [location] är fil, rad och kolumn. */
data class Violation(val name: String, val location: String)

/**
 * Deklarationer på toppnivå som syns utanför modulen, alltså varken är `internal` eller `private`.
 * I `:data` ska allt vara `internal`, så att `:app` bara når datalagret via interfacen i `:domain`.
 */
fun visibleOutsideModule(scope: KoScope): List<Violation> =
    scope.classes(includeNested = false, includeLocal = false).exposed() +
        scope.interfaces(includeNested = false).exposed() +
        scope.objects(includeNested = false).exposed() +
        scope.functions(includeNested = false, includeLocal = false).filter { it.isTopLevel }.exposed() +
        scope.properties(includeNested = false).filter { it.isTopLevel }.exposed() +
        scope.typeAliases.exposed()

/**
 * Funktioner och properties som syns utanför sin fil och använder Kotlins `Result` i sin typ.
 * Domänens API:er ska använda en egen resultattyp med typat fel, som `Outcome`.
 */
fun resultInApi(scope: KoScope): List<Violation> {
    val functions = scope.functions(includeLocal = false)
        .filter { it.isApi() }
        .filter { function ->
            function.returnType?.text.mentionsResult() || function.parameters.any { it.type.text.mentionsResult() }
        }
    val properties = scope.properties().filter { it.isApi() && it.type?.text.mentionsResult() }
    return functions.map { it.toViolation() } + properties.map { it.toViolation() }
}

/**
 * Muterbart tillstånd utanför DI: `var` eller muterbara samlingar och flöden i `object` och
 * `companion object`, och `var` på toppnivå. Tillstånd ska ägas av en ViewModel eller ett repository.
 */
fun mutableGlobalState(scope: KoScope): List<Violation> {
    val inObjects = scope.objects().flatMap { it.properties(includeNested = false) }.filter { it.isMutable() }
    val topLevel = scope.properties(includeNested = false).filter { it.isTopLevel && it.isMutable() }
    return (inObjects + topLevel).map { it.toViolation() }
}

private fun <T> List<T>.exposed(): List<Violation>
    where T : KoVisibilityModifierProvider, T : KoNameProvider, T : KoLocationProvider =
    filterNot { it.hasInternalModifier || it.hasPrivateModifier }.map { it.toViolation() }

private fun <T> T.isApi(): Boolean where T : KoVisibilityModifierProvider =
    !hasPrivateModifier && !hasInternalModifier

private fun <T> T.toViolation(): Violation where T : KoNameProvider, T : KoLocationProvider =
    Violation(name, location)

/** `Result<…>` eller `kotlin.Result<…>`, men inte namn som `ConnectResult<…>`. */
private val KOTLIN_RESULT = Regex("""(^|[^\w.])(kotlin\.)?Result<""")

private fun String?.mentionsResult(): Boolean = this != null && KOTLIN_RESULT.containsMatchIn(this)

/** Typer och fabriksfunktioner för samlingar, flöden och atomära värden som kan ändras. */
private val MUTABLE_TYPE = Regex(
    """\b(Mutable\w+|mutable\w+Of|ArrayList|HashMap|HashSet|LinkedHashMap|ConcurrentHashMap|Atomic\w+)\b""",
)

private fun KoPropertyDeclaration.isMutable(): Boolean =
    hasVarModifier || MUTABLE_TYPE.containsMatchIn(type?.text.orEmpty()) || MUTABLE_TYPE.containsMatchIn(value.orEmpty())
