package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class StateDecodingTest {

    private val info = NodeInfo(
        firmwareVersion = "0.4.0",
        protocolVersion = 3,
        name = "TinkerTapNode-A1B2",
        pairingRequired = true,
        authRequired = true,
        authTimeout = 10.seconds,
        heartbeatTimeout = null,
        outputs = listOf(
            Output(OutputId(0), "led", OutputType.Digital),
            Output(OutputId(1), "fan", OutputType.Pwm),
            Output(OutputId(2), "servo", OutputType.Servo),
        ),
    )

    @Test
    fun `decodes the example from the protocol spec`() {
        // led på, fan 128 och servo 90 grader.
        assertEquals(Outcome.Success(listOf(1, 128, 90)), decodeState(hex("01 80 5A"), info))
    }

    @Test
    fun `reads values above 127 as unsigned`() {
        assertEquals(Outcome.Success(listOf(0, 255, 180)), decodeState(hex("00 FF B4"), info))
    }

    @Test
    fun `rejects a state that does not have one value per output`() {
        assertEquals(
            Outcome.Failure(NodeError.StateMismatch(expectedOutputs = 3, actualValues = 2)),
            decodeState(hex("01 80"), info),
        )
    }
}
