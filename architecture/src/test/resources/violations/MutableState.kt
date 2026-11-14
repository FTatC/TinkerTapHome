// Exempel för ArchitectureRulesTest. Kompileras inte: filen ligger bland testresurserna.
package io.github.ftatc.tinkertaphome.example

import kotlinx.coroutines.flow.MutableStateFlow

object Counter {
    var count = 0
}

object Cache {
    val items = mutableListOf<String>()
}

class Holder {
    companion object {
        val state = MutableStateFlow(0)
    }
}

var topLevelCount = 0

object Constants {
    const val MAX = 3
    val names = listOf("Lampa", "Relä")
}

class Owner {
    // Tillstånd i en vanlig klass är tillåtet: klassen injiceras och kan bytas ut i tester.
    private var ownedCount = 0
}
