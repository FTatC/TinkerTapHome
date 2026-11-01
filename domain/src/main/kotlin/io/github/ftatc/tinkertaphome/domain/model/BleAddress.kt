package io.github.ftatc.tinkertaphome.domain.model

/**
 * En BLE-adress i normaliserad form: sex hexadecimala par med versaler, avgränsade av kolon,
 * till exempel `AA:BB:CC:DD:EE:FF`.
 *
 * Skapas bara via [parse]. Då är två adresser lika om och endast om de pekar på samma enhet,
 * oavsett om källan skrev dem med gemener eller versaler.
 */
@JvmInline
value class BleAddress private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private val FORMAT = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")

        /** Returnerar en normaliserad adress, eller `null` om [raw] inte är en giltig BLE-adress. */
        fun parse(raw: String): BleAddress? {
            val normalized = raw.trim().uppercase()
            return if (FORMAT.matches(normalized)) BleAddress(normalized) else null
        }
    }
}
