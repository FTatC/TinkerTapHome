package io.github.ftatc.tinkertaphome.ui.save

import io.github.ftatc.tinkertaphome.domain.model.BleAddress
import io.github.ftatc.tinkertaphome.domain.node.AuthToken
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.usecase.SaveNodeError

/**
 * UI-tillståndet för dialogen som sparar en hittad TinkerTapNode. Dialogen går igenom stegen i
 * [SaveStep] (fråga 7 i `docs/open-questions.md`).
 *
 * @property info Nodens Info, när parkopplingen har lyckats.
 * @property token Tokenet för AUTH. Förifyllt med firmwarens standardtoken (fråga 2).
 * @property pairError Felet från det senaste försöket att parkoppla, eller `null`.
 * @property saveError Felet från det senaste försöket att spara, eller `null`.
 * @property tokenInvalid `true` om tokenet inte är 1–19 synliga ASCII-tecken.
 */
data class SaveDeviceUiState(
    val address: BleAddress,
    val name: String,
    val step: SaveStep = SaveStep.PressButton,
    val info: NodeInfo? = null,
    val token: String = AuthToken.DEFAULT.value,
    val pairError: NodeFailure? = null,
    val saveError: SaveNodeError? = null,
    val tokenInvalid: Boolean = false,
) {
    /** Fältet för token visas bara om noden kräver AUTH (fråga 2). */
    val showsTokenField: Boolean
        get() = info?.authRequired == true

    /** Dialogen stänger sig och går tillbaka till listan när noden är sparad. */
    val isSaved: Boolean
        get() = step == SaveStep.Saved
}

/** Stegen i dialogen, i ordning. */
enum class SaveStep {
    /** Användaren ska trycka på nodens parkopplingsknapp och sedan på Parkoppla. */
    PressButton,

    /** Appen parkopplar och läser nodens Info. */
    Pairing,

    /** Användaren anger namn och, om noden kräver det, token. */
    Details,

    /** Appen skickar AUTH och sparar noden. */
    Saving,

    /** Noden är sparad. */
    Saved,
}
