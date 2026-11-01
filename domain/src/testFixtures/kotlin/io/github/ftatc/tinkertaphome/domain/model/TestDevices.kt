package io.github.ftatc.tinkertaphome.domain.model

/**
 * Gemensam testdata för alla modulers tester.
 *
 * Får [Device] ett nytt fält räcker det att uppdatera den här filen.
 */
fun testDevice(
    id: Long = 1,
    name: String = "Lampa",
    address: String = testAddress(id),
): Device = Device(id = id, address = address, name = name)

/** En giltig BLE-adress som är unik per [id], till exempel `AA:BB:CC:DD:EE:01` för id 1. */
fun testAddress(id: Long): String = "AA:BB:CC:DD:EE:%02X".format(id)
