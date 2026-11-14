package io.github.ftatc.tinkertaphome.domain.model

/**
 * Gemensam testdata för alla modulers tester.
 *
 * Får [Device] ett nytt fält räcker det att uppdatera den här filen.
 */
fun testDevice(
    id: Long = 1,
    name: String = "Lampa",
    address: BleAddress = testAddress(id),
): Device = Device(id = id, address = address, name = name)

/** En giltig BLE-adress som är unik per [id], till exempel `AA:BB:CC:DD:EE:01` för id 1. */
fun testAddress(id: Long): BleAddress =
    checkNotNull(BleAddress.parse("AA:BB:CC:DD:EE:%02X".format(id))) { "Ogiltigt test-id: $id" }

/** En skannad enhet för tester. Adressen är unik per [id] och matchar [testDevice] med samma id. */
/** En skannad enhet. Är en TinkerTapNode som standard, eftersom bara noder kan sparas. */
fun testScannedDevice(
    id: Long = 1,
    name: String? = "Lampa",
    rssi: Int = -60,
    isTinkerTapNode: Boolean = true,
): ScannedDevice = ScannedDevice(address = testAddress(id), name = name, rssi = rssi, isTinkerTapNode = isTinkerTapNode)
