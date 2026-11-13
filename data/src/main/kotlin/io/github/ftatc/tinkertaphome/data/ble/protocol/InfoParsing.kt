package io.github.ftatc.tinkertaphome.data.ble.protocol

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.flatMap
import io.github.ftatc.tinkertaphome.domain.model.DeviceNames
import io.github.ftatc.tinkertaphome.domain.node.NodeError
import io.github.ftatc.tinkertaphome.domain.node.NodeInfo
import io.github.ftatc.tinkertaphome.domain.node.Output
import io.github.ftatc.tinkertaphome.domain.node.OutputId
import io.github.ftatc.tinkertaphome.domain.node.OutputType
import java.nio.charset.CharacterCodingException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Tolkar Info, en JSON-sträng i UTF-8 (se `docs/ble-protocol.md`).
 *
 * Protokollversionen kontrolleras först, så att en nod med ett annat protokoll ger rätt fel även
 * om resten av JSON:en ser annorlunda ut. Okända fält ignoreras, eftersom firmwaren lägger till
 * fält utan att höja protokollversionen.
 */
internal fun parseInfo(bytes: ByteArray): Outcome<NodeInfo, NodeError> =
    parseJson(bytes).flatMap { element ->
        checkProtocolVersion(element).flatMap { decodeInfo(element) }
    }

private val json = Json { ignoreUnknownKeys = true }

private fun parseJson(bytes: ByteArray): Outcome<JsonElement, NodeError> =
    try {
        Outcome.Success(json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)))
    } catch (_: CharacterCodingException) {
        Outcome.Failure(NodeError.InvalidInfo)
    } catch (_: IllegalArgumentException) {
        // SerializationException ärver från IllegalArgumentException.
        Outcome.Failure(NodeError.InvalidInfo)
    }

private fun checkProtocolVersion(element: JsonElement): Outcome<Unit, NodeError> {
    val info = element as? JsonObject ?: return Outcome.Failure(NodeError.InvalidInfo)
    val proto = info[PROTO_FIELD] ?: return Outcome.Failure(NodeError.NodeTooOld(protocolVersion = null))
    val version = (proto as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: return Outcome.Failure(NodeError.InvalidInfo)
    return when {
        version < NodeProtocol.VERSION -> Outcome.Failure(NodeError.NodeTooOld(version))
        version > NodeProtocol.VERSION -> Outcome.Failure(NodeError.AppTooOld(version))
        else -> Outcome.Success(Unit)
    }
}

private fun decodeInfo(element: JsonElement): Outcome<NodeInfo, NodeError> =
    try {
        json.decodeFromJsonElement(InfoJson.serializer(), element).toNodeInfo()
    } catch (_: IllegalArgumentException) {
        Outcome.Failure(NodeError.InvalidInfo)
    }

private const val PROTO_FIELD = "proto"

/** Info som den skickas av noden. Alla fält krävs i protokollversion 3. */
@Serializable
private data class InfoJson(
    @SerialName("fw") val firmware: String,
    @SerialName(PROTO_FIELD) val protocolVersion: Int,
    @SerialName("name") val name: String,
    @SerialName("pair") val pairing: Int,
    @SerialName("auth") val auth: Int,
    @SerialName("ato") val authTimeoutMs: Long,
    @SerialName("hb") val heartbeatMs: Long,
    @SerialName("dev") val outputs: List<OutputJson>,
)

@Serializable
private data class OutputJson(
    @SerialName("id") val id: Int,
    @SerialName("n") val name: String,
    @SerialName("t") val type: String,
)

private fun InfoJson.toNodeInfo(): Outcome<NodeInfo, NodeError> {
    val pairingRequired = pairing.toFlag()
    val authRequired = auth.toFlag()
    val valid = pairingRequired != null && authRequired != null && authTimeoutMs >= 0 && heartbeatMs >= 0 &&
        outputs.size in 1..NodeInfo.MAX_OUTPUTS && outputs.withIndex().all { (index, output) -> output.id == index }
    if (!valid) return Outcome.Failure(NodeError.InvalidInfo)
    return Outcome.Success(
        NodeInfo(
            firmwareVersion = DeviceNames.clean(firmware),
            protocolVersion = protocolVersion,
            name = DeviceNames.truncate(DeviceNames.clean(name)),
            pairingRequired = pairingRequired,
            authRequired = authRequired,
            authTimeout = authTimeoutMs.milliseconds,
            heartbeatTimeout = heartbeatMs.takeIf { it > 0 }?.milliseconds,
            outputs = outputs.map { it.toOutput() },
        ),
    )
}

private fun OutputJson.toOutput() = Output(
    id = OutputId(id),
    name = DeviceNames.truncate(DeviceNames.clean(name)),
    type = when (type) {
        "D" -> OutputType.Digital
        "P" -> OutputType.Pwm
        "S" -> OutputType.Servo
        else -> OutputType.Unknown(DeviceNames.clean(type))
    },
)

/** `0` och `1` är de enda giltiga värdena för flaggor i Info. */
private fun Int.toFlag(): Boolean? = when (this) {
    0 -> false
    1 -> true
    else -> null
}
