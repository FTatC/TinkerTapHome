package io.github.ftatc.tinkertaphome.domain.model

/**
 * En BLE-enhet som användaren har sparat i appen.
 *
 * @property id Lokalt id som appen tilldelar. Stabilt även om enhetens namn ändras.
 * @property address Enhetens BLE-adress i normaliserad form, se [BleAddress].
 * @property name Namnet som visas för användaren.
 */
data class Device(
    val id: Long,
    val address: BleAddress,
    val name: String,
)
