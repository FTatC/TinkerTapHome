package io.github.ftatc.tinkertaphome.ui.device

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
    is ConnectionError.ConnectFailed -> stringResource(R.string.connection_error_connect_failed, error.status)
    ConnectionError.BondFailed -> stringResource(R.string.connection_error_bond_failed)
    ConnectionError.PairingMissing -> stringResource(R.string.connection_error_pairing_missing)
    ConnectionError.NoReply -> stringResource(R.string.connection_error_no_reply)
    is ConnectionError.CharacteristicNotFound -> stringResource(R.string.connection_error_characteristic_not_found)
    ConnectionError.RequestRejected -> stringResource(R.string.connection_error_rejected)
    is ConnectionError.OperationFailed -> stringResource(R.string.connection_error_operation_failed, error.status)
}
