package io.github.ftatc.tinkertaphome.ui.permissions

/** Utfallet av en behörighetsförfrågan, tolkat så att ViewModeln inte behöver Android-API:er. */
enum class PermissionRequestResult {
    Granted,

    /** Användaren nekade men kan tillfrågas igen. */
    Denied,

    /** Användaren nekade och Android visar inte dialogen igen. Behörigheten måste slås på i inställningarna. */
    PermanentlyDenied,
}

/**
 * Tolkar resultatet från `RequestMultiplePermissions`.
 *
 * @param results behörighet → beviljad. Är tom om användaren stängde dialogen utan att välja.
 * @param shouldShowRationale Androids `shouldShowRequestPermissionRationale`. Efter ett nej betyder
 * `false` att Android inte längre visar dialogen.
 */
internal fun interpretPermissionResult(
    results: Map<String, Boolean>,
    shouldShowRationale: (String) -> Boolean,
): PermissionRequestResult {
    val denied = results.filterValues { granted -> !granted }.keys
    return when {
        results.isEmpty() -> PermissionRequestResult.Denied
        denied.isEmpty() -> PermissionRequestResult.Granted
        denied.any(shouldShowRationale) -> PermissionRequestResult.Denied
        else -> PermissionRequestResult.PermanentlyDenied
    }
}
