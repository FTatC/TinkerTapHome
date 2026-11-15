package io.github.ftatc.tinkertaphome.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Användarens inställningar (steg 7.4 i `docs/roadmap.md`). Standardvärdena ger samma beteende som
 * innan inställningarna fanns.
 *
 * @property onlyNodesInScan `true` om skanningen bara ska visa TinkerTapNodes, som är de enda enheter
 * som går att spara.
 */
data class AppSettings(
    val scanDuration: ScanDuration = ScanDuration.DEFAULT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onlyNodesInScan: Boolean = false,
)

/** Hur länge skanningen pågår innan den stoppas för att spara batteri. */
enum class ScanDuration(val duration: Duration) {
    SHORT(15.seconds),
    NORMAL(30.seconds),
    LONG(60.seconds),
    ;

    companion object {
        val DEFAULT = NORMAL
    }
}

/** Om appen är ljus, mörk eller följer systemets inställning. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Förväntade fel när inställningarna sparas. */
sealed interface SettingsError {

    /** Inställningarna gick inte att skriva, till exempel för att lagringen är full. */
    data object StorageUnavailable : SettingsError
}
