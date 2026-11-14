// Exempel för ArchitectureRulesTest. Kompileras inte: filen ligger bland testresurserna.
package io.github.ftatc.tinkertaphome.data.example

class PublicClass

interface PublicInterface

object PublicObject

fun publicFunction() = Unit

val publicProperty = 1

typealias PublicAlias = String

internal class InternalClass {
    // Medlemmar i en internal klass syns ändå inte utanför modulen.
    fun member() = Unit
}

private fun privateFunction() = Unit

internal val internalProperty = 2
