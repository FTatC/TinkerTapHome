package io.github.ftatc.tinkertaphome.domain.model

/** Förväntade fel när en enhet sparas. */
sealed interface SaveDeviceError {

    /** Namnet är tomt eller består bara av blanksteg. */
    data object NameMissing : SaveDeviceError

    /** En enhet med samma adress är redan sparad. */
    data object AlreadySaved : SaveDeviceError

    /** Den lokala lagringen gick inte att skriva till. */
    data object StorageUnavailable : SaveDeviceError
}
