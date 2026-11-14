package io.github.ftatc.tinkertaphome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ftatc.tinkertaphome.R
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import io.github.ftatc.tinkertaphome.domain.node.OutputValue
import kotlin.math.roundToInt

/** En utgång med en kontroll som passar dess typ. Kontrollen har utgångens namn som beskrivning. */
@Composable
fun OutputControl(
    output: OutputValue,
    enabled: Boolean,
    onOutputChange: (Output, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (output.output.type) {
        OutputType.Digital -> DigitalControl(output, enabled, onOutputChange, modifier)
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
    onOutputChange: (Output, Int) -> Unit,
    modifier: Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(output.output.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = output.value != 0,
            onCheckedChange = { on -> onOutputChange(output.output, if (on) 1 else 0) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = output.output.name },
        )
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
    val shown = dragged ?: output.value.toFloat()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(output.output.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
            modifier = Modifier.semantics { contentDescription = output.output.name },
        )
    }
}

@Composable
private fun UnknownOutput(output: Output, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(output.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.device_output_unknown), style = MaterialTheme.typography.bodyMedium)
    }
}
