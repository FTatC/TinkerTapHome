package io.github.ftatc.tinkertaphome.ui.navigation

import kotlinx.serialization.Serializable

/** Startskärmen med sparade enheter. */
@Serializable
data object DeviceListRoute

/** Skärmen som söker efter nya enheter i närheten. */
@Serializable
data object ScanRoute

/**
 * Dialogen där en hittad enhet namnges och sparas.
 *
 * @property address Enhetens BLE-adress. Skickas som text eftersom rutter bara kan ha enkla typer.
 * @property suggestedName Namnet som enheten annonserar, eller `null`.
 */
@Serializable
data class SaveDeviceRoute(val address: String, val suggestedName: String?)
