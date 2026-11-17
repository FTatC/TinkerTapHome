package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.node.OutputValue
import kotlin.math.roundToInt

/** Händelserna från en digital utgång som kan visas som tryckknapp. */
data class PushButtonActions(
    val onPulse: (Output) -> Unit = {},
    val onPushButtonChange: (Output, Boolean) -> Unit = { _, _ -> },
)

/**
 * En utgång med en kontroll som passar dess typ. Kontrollen har utgångens namn som beskrivning.
 *
 * En digital utgång visas som switch, eller som en knapp som skickar en puls om [isPushButton] är
 * `true`. Användaren byter mellan dem i utgångens meny, som går att använda även när noden inte är
 * ansluten eftersom valet bara sparas i appen.
 */
@Composable
fun OutputControl(
    output: OutputValue,
    enabled: Boolean,
    onOutputChange: (Output, Int) -> Unit,
    modifier: Modifier = Modifier,
    isPushButton: Boolean = false,
    pushButtonActions: PushButtonActions = PushButtonActions(),
) {
    when (output.output.type) {
        OutputType.Digital -> DigitalControl(output, enabled, isPushButton, onOutputChange, pushButtonActions, modifier)
        OutputType.Pwm -> SliderControl(output, OutputType.PWM_RANGE, enabled, onOutputChange, modifier) {
            stringResource(R.string.device_output_percent, pwmPercent(it))
        }
        OutputType.Servo -> SliderControl(output, OutputType.SERVO_RANGE, enabled, onOutputChange, modifier) {
            stringResource(R.string.device_output_degrees, it)
        }
        is OutputType.Unknown -> UnknownOutput(output.output, modifier)
    }
}

@Composable
private fun DigitalControl(
    output: OutputValue,
    enabled: Boolean,
    isPushButton: Boolean,
    onOutputChange: (Output, Int) -> Unit,
    pushButtonActions: PushButtonActions,
    modifier: Modifier,
) {
    val label = outputLabel(output.output)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (isPushButton) {
            PulseButton(output, label, enabled, pushButtonActions.onPulse)
        } else {
            Switch(
                checked = output.value != 0,
                onCheckedChange = { on -> onOutputChange(output.output, if (on) 1 else 0) },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        DigitalOutputMenu(output.output, label, isPushButton, pushButtonActions.onPushButtonChange)
    }
}

/** Knappen som skickar en puls. Medan pulsen pågår visar State att utgången är på. */
@Composable
private fun PulseButton(output: OutputValue, label: String, enabled: Boolean, onPulse: (Output) -> Unit) {
    if (output.value != 0) {
        Text(
            stringResource(R.string.device_output_on),
            modifier = Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Button(
        onClick = { onPulse(output.output) },
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(stringResource(R.string.device_output_press))
    }
}

/** Menyn där användaren väljer om utgången visas som switch eller tryckknapp. */
@Composable
private fun DigitalOutputMenu(
    output: Output,
    label: String,
    isPushButton: Boolean,
    onPushButtonChange: (Output, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.device_output_options, label),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choice = if (isPushButton) R.string.device_output_show_as_switch else R.string.device_output_show_as_push_button
            DropdownMenuItem(
                text = { Text(stringResource(choice)) },
                onClick = {
                    expanded = false
                    onPushButtonChange(output, !isPushButton)
                },
            )
        }
    }
}

/**
 * Ett reglage som visar sitt eget värde medan användaren drar och skickar det först när användaren
 * släpper. Sedan visas nodens State igen, så att reglaget aldrig visar ett värde som noden inte har.
 */
@Composable
private fun SliderControl(
    output: OutputValue,
    range: IntRange,
    enabled: Boolean,
    onOutputChange: (Output, Int) -> Unit,
    modifier: Modifier,
    valueLabel: @Composable (Int) -> String,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val label = outputLabel(output.output)
    // Ett värde i State utanför intervallet visas som gränsen, precis som reglaget själv visar det.
    val shown = dragged ?: output.value.coerceIn(range).toFloat()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(valueLabel(shown.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = shown,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { onOutputChange(output.output, it.roundToInt()) }
                dragged = null
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun UnknownOutput(output: Output, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(outputLabel(output), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.device_output_unknown), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Utgångens namn, eller "Utgång 0" om namnet är tomt. Namnet kommer från nodens Info och kan bli
 * tomt när osynliga tecken har rensats bort. Utan reservtext får kontrollen ingen rubrik, och
 * TalkBack läser ingen beskrivning.
 */
@Composable
private fun outputLabel(output: Output): String =
    output.name.ifEmpty { stringResource(R.string.device_output_fallback, output.id.value) }
