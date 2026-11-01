package io.github.ftatc.tinkertaphome.domain

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
