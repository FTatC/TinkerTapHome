package io.github.ftatc.tinkertaphome.ui.device

import io.github.ftatc.tinkertaphome.domain.node.OutputType
import kotlin.math.roundToInt

/** Ett PWM-värde i [OutputType.PWM_RANGE] som avrundad procent, så att 128 blir 50 %. */
fun pwmPercent(value: Int): Int = (value * PERCENT.toFloat() / OutputType.PWM_RANGE.last).roundToInt()

private const val PERCENT = 100
