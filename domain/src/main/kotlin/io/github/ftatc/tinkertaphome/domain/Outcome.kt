package io.github.ftatc.tinkertaphome.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

/**
 * Resultatet av en operation som kan misslyckas på ett förväntat sätt.
 *
 * Feltypen [E] är typad, så anroparen måste hantera varje fel med ett uttömmande `when`.
 * Används i stället för Kotlins `Result` i domänens API:er (se CLAUDE.md, "Fel är data").
 */
sealed interface Outcome<out T, out E> {

    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

/** Omvandlar värdet vid [Outcome.Success] och lämnar [Outcome.Failure] orörd. */
inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** Omvandlar felet vid [Outcome.Failure] och lämnar [Outcome.Success] orörd. */
inline fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> Outcome.Failure(transform(error))
}

/** Kör nästa steg med värdet vid [Outcome.Success]. Vid [Outcome.Failure] körs inget mer och felet skickas vidare. */
inline fun <T, E, R> Outcome<T, E>.flatMap(next: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> next(value)
    is Outcome.Failure -> this
}

/**
 * Skickar vidare varje resultat och avslutar flödet direkt efter det första [Outcome.Failure].
 * Felet skickas alltså med, men inget efter det.
 */
fun <T, E> Flow<Outcome<T, E>>.takeUntilFailure(): Flow<Outcome<T, E>> =
    transformWhile { outcome ->
        emit(outcome)
        outcome is Outcome.Success
    }
