package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.node.CommandError
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeFailure

/** Ett felmeddelande för ett misslyckat anrop till en TinkerTapNode. */
@Composable
fun nodeFailureMessage(failure: NodeFailure): String = when (failure) {
    is NodeFailure.Connection -> connectionErrorMessage(failure.error)
    is NodeFailure.Node -> nodeErrorMessage(failure.error)
    is NodeFailure.Command -> commandErrorMessage(failure.error)
}

@Composable
private fun nodeErrorMessage(error: NodeError): String = when (error) {
    NodeError.InvalidInfo -> stringResource(R.string.node_error_invalid_info)
    is NodeError.NodeTooOld -> error.protocolVersion
        ?.let { stringResource(R.string.node_error_node_too_old_version, it) }
        ?: stringResource(R.string.node_error_node_too_old)
    is NodeError.AppTooOld -> stringResource(R.string.node_error_app_too_old, error.protocolVersion)
    is NodeError.StateMismatch -> stringResource(R.string.node_error_state_mismatch)
}

@Composable
private fun commandErrorMessage(error: CommandError): String = when (error) {
    CommandError.AuthFailed -> stringResource(R.string.node_error_auth_failed)
    CommandError.NotAuthorized -> stringResource(R.string.node_error_not_authorized)
    CommandError.InvalidOutput -> stringResource(R.string.node_error_invalid_output)
    CommandError.UnknownCommand,
    CommandError.WrongType,
    CommandError.MissingValue,
    CommandError.BadLength,
    -> stringResource(R.string.node_error_rejected)
    is CommandError.Unknown -> stringResource(R.string.node_error_unknown_status, error.status)
}
