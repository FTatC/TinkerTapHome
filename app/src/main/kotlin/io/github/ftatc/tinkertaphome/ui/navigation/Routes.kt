package io.github.ftatc.tinkertaphome.ui.navigation

import kotlinx.serialization.Serializable

/** Startskärmen med sparade enheter. */
@Serializable
data object DeviceListRoute

/** Skärmen som söker efter nya enheter i närheten. */
@Serializable
data object ScanRoute
