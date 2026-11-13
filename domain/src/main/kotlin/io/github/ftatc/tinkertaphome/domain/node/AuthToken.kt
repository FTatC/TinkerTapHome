package io.github.ftatc.tinkertaphome.domain.node

/**
 * Tokenet som appen skickar med `AUTH`. Det är 1–[MAX_LENGTH] synliga ASCII-tecken och måste
 * stämma exakt med nodens token, så det trimmas inte.
 *
 * Skapas bara via [parse]. [toString] visar aldrig tokenet, så att det inte hamnar i loggar.
 */
@JvmInline
value class AuthToken private constructor(val value: String) {

    override fun toString(): String = "AuthToken(***)"

    companion object {
        /** Längsta tillåtna token. Ett kommando är högst 20 byte, och opcoden tar en. */
        const val MAX_LENGTH = 19

        /** Firmwarens standardtoken, som föreslås när en nod sparas. */
        val DEFAULT: AuthToken = AuthToken("tinker")

        /** Returnerar tokenet, eller `null` om [raw] är tomt, för långt eller innehåller annat än synliga ASCII-tecken. */
        fun parse(raw: String): AuthToken? =
            if (raw.length in 1..MAX_LENGTH && raw.all { it in VISIBLE_ASCII }) AuthToken(raw) else null

        private val VISIBLE_ASCII = ' '..'~'
    }
}
