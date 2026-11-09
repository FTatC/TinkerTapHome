package io.github.ftatc.tinkertaphome.domain.model

/** En sparad enhet tillsammans med dess anslutningsstatus just nu. */
data class ConnectedDevice(
    val device: Device,
    val connection: ConnectionState,
)
