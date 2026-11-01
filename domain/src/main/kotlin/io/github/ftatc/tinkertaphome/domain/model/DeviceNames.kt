package io.github.ftatc.tinkertaphome.domain.model

/**
 * Regler för enhetsnamn. Namn kommer både från användaren och från enheternas reklampaket, som vem
 * som helst i närheten kan skicka, och behandlas därför som osäker indata.
 */
object DeviceNames {

    /** Högsta tillåtna längd, räknad i Unicode-tecken (kodpunkter). */
    const val MAX_LENGTH = 32

    /**
     * Tar bort kontrolltecken och osynliga formateringstecken, slår ihop blanksteg och tar bort
     * blanksteg i början och slutet. Formateringstecken (Unicode-kategori Cf) omfattar bland annat
     * tecken som vänder textriktningen och kan få ett namn att se ut som något annat.
     */
    fun clean(raw: String): String {
        val visible = buildString {
            raw.codePoints().forEach { codePoint ->
                if (!isHidden(codePoint)) appendCodePoint(codePoint)
            }
        }
        return visible.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Antal Unicode-tecken i [name]. Ett tecken utanför BMP, till exempel en emoji, räknas som ett. */
    fun length(name: String): Int = name.codePointCount(0, name.length)

    /** Kortar [name] till högst [MAX_LENGTH] tecken utan att dela ett tecken som består av två `Char`. */
    fun truncate(name: String): String =
        if (length(name) <= MAX_LENGTH) name else name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH)).trimEnd()

    private fun isHidden(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint)) return false
        val type = Character.getType(codePoint)
        return Character.isISOControl(codePoint) || type == Character.FORMAT.toInt()
    }

    private val WHITESPACE = Regex("\\s+")
}
