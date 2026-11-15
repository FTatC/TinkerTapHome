package io.github.ftatc.tinkertaphome.ui.device

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.model.ConnectionError
import io.github.ftatc.tinkertaphome.domain.model.ConnectionState

/** Kort text för anslutningsstatusen, till exempel "Ansluten". */
@Composable
fun connectionStateLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Disconnected -> R.string.connection_disconnected
        ConnectionState.Waiting -> R.string.connection_waiting
        ConnectionState.Connecting -> R.string.connection_connecting
        ConnectionState.Connected -> R.string.connection_connected
        is ConnectionState.Failed -> R.string.connection_failed
    },
)

/** Ett felmeddelande som användaren förstår och kan agera på. */
@Composable
fun connectionErrorMessage(error: ConnectionError): String = when (error) {
    ConnectionError.BluetoothOff -> stringResource(R.string.connection_error_bluetooth_off)
    ConnectionError.PermissionMissing -> stringResource(R.string.connection_error_permission)
    ConnectionError.NotConnected -> stringResource(R.string.connection_error_not_connected)
    ConnectionError.Timeout -> stringResource(R.string.connection_error_timeout)
    ConnectionError.ConnectionLost -> stringResource(R.string.connection_error_lost)
    is ConnectionError.ConnectFailed -> connectFailedMessage(error.status)?.let { stringResource(it) }
        ?: stringResource(R.string.connection_error_connect_failed, error.status)
    ConnectionError.BondFailed -> stringResource(R.string.connection_error_bond_failed)
    ConnectionError.PairingMissing -> stringResource(R.string.connection_error_pairing_missing)
    ConnectionError.NoReply -> stringResource(R.string.connection_error_no_reply)
    is ConnectionError.CharacteristicNotFound -> stringResource(R.string.connection_error_characteristic_not_found)
    ConnectionError.RequestRejected -> stringResource(R.string.connection_error_rejected)
    is ConnectionError.OperationFailed -> stringResource(R.string.connection_error_operation_failed, error.status)
}

/**
 * En text som förklarar varför anslutningen inte kunde upprättas, eller `null` för statusar som inte
 * har någon egen text. Då visas en allmän text med statusen.
 *
 * Statusarna kommer från Androids Bluetooth-stack (`GATT_CONN_*` och `GATT_ERROR`). De skrivs ut här
 * i stället för att importeras, så att `ui` inte använder Androids Bluetooth-API:er (se CLAUDE.md).
 */
@StringRes
fun connectFailedMessage(status: Int): Int? = when (status) {
    GATT_CONN_TIMEOUT -> R.string.connection_error_out_of_reach
    GATT_CONN_TERMINATE_PEER_USER -> R.string.connection_error_closed_by_device
    GATT_CONN_TERMINATE_LOCAL_HOST -> R.string.connection_error_closed_by_phone
    GATT_CONN_FAIL_ESTABLISH -> R.string.connection_error_establish_failed
    GATT_ERROR -> R.string.connection_error_gatt_error
    else -> null
}

/** Enheten slutade svara, oftast för att den kom utom räckhåll. */
private const val GATT_CONN_TIMEOUT = 8

/** Enheten stängde anslutningen. En TinkerTapNode tar bara emot en anslutning åt gången. */
private const val GATT_CONN_TERMINATE_PEER_USER = 19

/** Telefonen stängde anslutningen. */
private const val GATT_CONN_TERMINATE_LOCAL_HOST = 22

/** Anslutningen kunde inte upprättas, oftast för att enheten är för långt bort. */
private const val GATT_CONN_FAIL_ESTABLISH = 62

/** Ett allmänt fel i Androids Bluetooth-stack. Appen har redan försökt igen innan felet visas. */
private const val GATT_ERROR = 133
