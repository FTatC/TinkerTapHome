package io.github.ftatc.tinkertaphome.domain.model

/**
 * En BLE-enhet som användaren har sparat i appen.
 *
 * @property id Lokalt id som appen tilldelar. Stabilt även om enhetens namn ändras.
 * @property address Enhetens BLE-adress, till exempel `AA:BB:CC:DD:EE:FF`.
 * @property name Namnet som visas för användaren.
 */
data class Device(
    val id: Long,
    val address: String,
    val name: String,
)
