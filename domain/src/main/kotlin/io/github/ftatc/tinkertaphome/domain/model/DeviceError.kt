package io.github.ftatc.tinkertaphome.domain.model

/** Förväntade fel när sparade enheter läses eller skrivs. */
sealed interface DeviceError {

    /** Den lokala lagringen gick inte att läsa, till exempel på grund av en skadad databas. */
    data object StorageUnavailable : DeviceError
}
