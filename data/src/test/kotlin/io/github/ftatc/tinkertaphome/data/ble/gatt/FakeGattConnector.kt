package io.github.ftatc.tinkertaphome.data.ble.gatt

import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import java.util.UUID

/**
 * Fake för [GattConnector] och [GattHandle]. Svarar som standard direkt och lyckat på allt, som en
 * snäll enhet. Ändra [connectResponse], [respond] och [requestResult] för att simulera fel, och sätt
 * dem till `null` eller låt dem returnera `null` för att svara själv med [emit].
 */
internal class FakeGattConnector : GattConnector {

    var bluetoothEnabled = true

    /** Om satt startas ingen anslutning, och det här returneras i stället. */
    var refuseWith: GattConnectStart? = null

    /** Händelsen som skickas direkt när en anslutning startas, eller `null` för att svara själv. */
    var connectResponse: GattEvent? = GattEvent.ConnectionChanged(GATT_SUCCESS, connected = true)

    var bonded = true

    /** `autoConnect` för varje anslutning som har startats, i ordning. */
    val autoConnects = mutableListOf<Boolean>()

    var readValue = byteArrayOf(1, 2, 3)

    /** Svaret som skickas direkt när en operation startas, eller `null` för att svara själv. */
    var respond: (FakeRequest) -> GattEvent? = ::successFor

    /** Vad operationerna svarar när de startas. */
    var requestResult: (FakeRequest) -> GattRequest = { GattRequest.STARTED }

    var connectCount = 0
        private set

    /** Operationer som har startats, i ordning, över alla anslutningar. */
    val requests = mutableListOf<FakeRequest>()

    lateinit var handle: FakeGattHandle
        private set

    /** Alla anslutningar som har startats, i ordning. [handle] är den senaste. */
    val handles = mutableListOf<FakeGattHandle>()

    private var listener: ((GattEvent) -> Unit)? = null

    override fun isBluetoothEnabled(): Boolean = bluetoothEnabled

    override fun isBonded(address: String): Boolean = bonded

    override fun connect(address: String, autoConnect: Boolean, onEvent: (GattEvent) -> Unit): GattConnectStart {
        connectCount++
        autoConnects += autoConnect
        refuseWith?.let { return it }
        handle = FakeGattHandle(this)
        handles += handle
        listener = onEvent
        connectResponse?.let(::emit)
        return GattConnectStart.Started(handle)
    }

    /** Skickar en händelse som om den kom från Android. Efter [GattHandle.close] skickas inget, precis som i Android. */
    fun emit(event: GattEvent) {
        if (!handle.closed) listener?.invoke(event)
    }

    internal fun start(request: FakeRequest): GattRequest {
        requests += request
        val result = requestResult(request)
        if (result == GattRequest.STARTED) respond(request)?.let(::emit)
        return result
    }

    fun successFor(request: FakeRequest): GattEvent = when (request) {
        FakeRequest.DiscoverServices -> GattEvent.ServicesDiscovered(GATT_SUCCESS)
        FakeRequest.CreateBond -> GattEvent.BondChanged(BondState.BONDED)
        is FakeRequest.RequestMtu -> GattEvent.MtuChanged(request.mtu, GATT_SUCCESS)
        is FakeRequest.Read -> GattEvent.CharacteristicRead(request.characteristic, GATT_SUCCESS, readValue)
        is FakeRequest.Write -> GattEvent.CharacteristicWritten(request.characteristic, GATT_SUCCESS)
        is FakeRequest.SetNotifications -> GattEvent.NotificationsChanged(request.characteristic, GATT_SUCCESS)
    }
}

internal class FakeGattHandle(private val connector: FakeGattConnector) : GattHandle {

    var closed = false
        private set

    override fun discoverServices(): GattRequest = connector.start(FakeRequest.DiscoverServices)

    override fun isBonded(): Boolean = connector.bonded

    override fun createBond(): GattRequest = connector.start(FakeRequest.CreateBond)

    override fun requestMtu(mtu: Int): GattRequest = connector.start(FakeRequest.RequestMtu(mtu))

    override fun read(characteristic: CharacteristicId): GattRequest = connector.start(FakeRequest.Read(characteristic))

    override fun write(characteristic: CharacteristicId, value: ByteArray): GattRequest =
        connector.start(FakeRequest.Write(characteristic, value.toList()))

    override fun setNotifications(characteristic: CharacteristicId, enabled: Boolean): GattRequest =
        connector.start(FakeRequest.SetNotifications(characteristic, enabled))

    override fun close() {
        closed = true
    }
}

/** En operation som har startats på [FakeGattHandle]. */
internal sealed interface FakeRequest {
    data object DiscoverServices : FakeRequest
    data object CreateBond : FakeRequest
    data class RequestMtu(val mtu: Int) : FakeRequest
    data class Read(val characteristic: CharacteristicId) : FakeRequest
    data class Write(val characteristic: CharacteristicId, val value: List<Byte>) : FakeRequest
    data class SetNotifications(val characteristic: CharacteristicId, val enabled: Boolean) : FakeRequest
}

/** En characteristic för tester. Olika [id] ger olika characteristics i samma tjänst. */
internal fun testCharacteristic(id: Int = 1): CharacteristicId = CharacteristicId(
    service = UUID.fromString("0000aa00-0000-1000-8000-00805f9b34fb"),
    characteristic = UUID.fromString("0000aa%02x-0000-1000-8000-00805f9b34fb".format(id)),
)
