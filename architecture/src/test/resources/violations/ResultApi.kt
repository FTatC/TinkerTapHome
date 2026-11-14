// Exempel för ArchitectureRulesTest. Kompileras inte: filen ligger bland testresurserna.
package io.github.ftatc.tinkertaphome.domain.example

import kotlinx.coroutines.flow.Flow

interface Example {
    fun readValue(): Result<Int>

    fun observeValues(): Flow<Result<Int>>

    fun takeResult(result: kotlin.Result<Int>)

    val lastResult: Result<Int>?

    // En egen resultattyp med typat fel är tillåten.
    fun connect(): ConnectResult<Int>
}

private fun hiddenHelper(): Result<Int> = runCatching { 1 }
