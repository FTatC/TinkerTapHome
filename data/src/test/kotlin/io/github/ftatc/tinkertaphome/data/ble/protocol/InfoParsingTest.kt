package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class InfoParsingTest {

    @Test
    fun `parses the example from the protocol spec`() {
        val expected = NodeInfo(
            firmwareVersion = "0.4.0",
            protocolVersion = 3,
            name = "TinkerTapNode-A1B2",
            pairingRequired = true,
            authRequired = true,
            authTimeout = 10.seconds,
            heartbeatTimeout = null,
            outputs = listOf(Output(OutputId(0), "led", OutputType.Digital)),
        )

        assertEquals(Outcome.Success(expected), parse(SPEC_EXAMPLE))
    }

    @Test
    fun `parses every output type in id order`() {
        val info = parse(info(dev = """[{"id":0,"n":"led","t":"D"},{"id":1,"n":"fan","t":"P"},{"id":2,"n":"servo","t":"S"}]"""))

        assertEquals(
            listOf(OutputType.Digital, OutputType.Pwm, OutputType.Servo),
            info.successValue().outputs.map { it.type },
        )
    }

    @Test
    fun `keeps an output with an unknown type so it can be shown but not controlled`() {
        val info = parse(info(dev = """[{"id":0,"n":"led","t":"X"}]"""))

        assertEquals(OutputType.Unknown("X"), info.successValue().outputs.single().type)
    }

    @Test
    fun `ignores unknown fields`() {
        assertEquals(parse(SPEC_EXAMPLE), parse(SPEC_EXAMPLE.replace("\"hb\":0", "\"hb\":0,\"new\":{\"a\":1}")))
    }

    @Test
    fun `reads a heartbeat timeout and turned off authentication`() {
        val info = parse(info(auth = 0, ato = 0, hb = 3000)).successValue()

        assertEquals(false, info.authRequired)
        assertEquals(Duration.ZERO, info.authTimeout)
        assertEquals(3.seconds, info.heartbeatTimeout)
    }

    @Test
    fun `reports a node without protocol version as too old`() {
        assertEquals(
            Outcome.Failure(NodeError.NodeTooOld(protocolVersion = null)),
            parse("""{"fw":"0.1.0","name":"TinkerTapNode","dev":[{"id":0,"n":"led","t":"D"}]}"""),
        )
    }

    @Test
    fun `reports an older protocol as a node that needs new firmware`() {
        assertEquals(Outcome.Failure(NodeError.NodeTooOld(protocolVersion = 2)), parse(info(proto = 2)))
    }

    @Test
    fun `reports a newer protocol as an app that needs an update`() {
        // Ett nyare protokoll kan ha ändrat andra fält, så resten av Info tolkas inte.
        assertEquals(Outcome.Failure(NodeError.AppTooOld(protocolVersion = 4)), parse("""{"proto":4,"changed":true}"""))
    }

    @Test
    fun `rejects info that is not valid JSON`() {
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse("""{"fw":"0.4.0","proto":3"""))
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse("""[3]"""))
    }

    @Test
    fun `rejects info that is not valid UTF-8`() {
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parseInfo(byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)))
    }

    @Test
    fun `rejects a protocol version that is not a number`() {
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse(SPEC_EXAMPLE.replace("\"proto\":3", "\"proto\":\"3\"")))
    }

    @Test
    fun `rejects info with a missing field`() {
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse(SPEC_EXAMPLE.replace("\"auth\":1,", "")))
    }

    @Test
    fun `rejects a flag that is neither 0 nor 1`() {
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse(info(pair = 2)))
    }

    @Test
    fun `rejects outputs that are not in id order`() {
        // Appen får inte gissa vilket State-värde som hör till vilken utgång.
        assertEquals(
            Outcome.Failure(NodeError.InvalidInfo),
            parse(info(dev = """[{"id":1,"n":"fan","t":"P"},{"id":0,"n":"led","t":"D"}]""")),
        )
    }

    @Test
    fun `rejects a node without outputs or with more than 20`() {
        val tooMany = (0..NodeInfo.MAX_OUTPUTS).joinToString(",", "[", "]") { """{"id":$it,"n":"o$it","t":"D"}""" }

        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse(info(dev = "[]")))
        assertEquals(Outcome.Failure(NodeError.InvalidInfo), parse(info(dev = tooMany)))
    }

    @Test
    fun `removes hidden characters from names sent by the node`() {
        val info = parse(info(name = "Nod\\u202E1", dev = """[{"id":0,"n":"le\u0000d","t":"D"}]""")).successValue()

        assertEquals("Nod1", info.name)
        assertEquals("led", info.outputs.single().name)
    }

    private fun parse(json: String): Outcome<NodeInfo, NodeError> = parseInfo(json.encodeToByteArray())

    private fun Outcome<NodeInfo, NodeError>.successValue(): NodeInfo = (this as Outcome.Success).value

    private fun info(
        proto: Int = 3,
        name: String = "TinkerTapNode-A1B2",
        pair: Int = 1,
        auth: Int = 1,
        ato: Int = 10000,
        hb: Int = 0,
        dev: String = """[{"id":0,"n":"led","t":"D"}]""",
    ): String = """{"fw":"0.4.0","proto":$proto,"name":"$name","pair":$pair,"auth":$auth,"ato":$ato,"hb":$hb,"dev":$dev}"""

    private companion object {
        /** Exemplet i avsnittet "Info" i `docs/ble-protocol.md`. */
        const val SPEC_EXAMPLE =
            """{"fw":"0.4.0","proto":3,"name":"TinkerTapNode-A1B2","pair":1,"auth":1,"ato":10000,"hb":0,"dev":[{"id":0,"n":"led","t":"D"}]}"""
    }
}
