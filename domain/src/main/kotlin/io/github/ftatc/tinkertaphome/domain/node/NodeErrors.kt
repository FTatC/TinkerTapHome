package io.github.ftatc.tinkertaphome.domain.node

/** Fel i det en nod berättar om sig själv, som gör att appen inte kan styra den. */
sealed interface NodeError {

    /** Info är inte giltig JSON eller saknar fält som protokollet kräver. */
    data object InvalidInfo : NodeError

    /**
     * Noden kör ett äldre protokoll än appen stöder och behöver ny firmware.
     * [protocolVersion] är `null` om Info saknar protokollversion, som i den första firmwaren.
     */
    data class NodeTooOld(val protocolVersion: Int?) : NodeError

    /** Noden kör ett nyare protokoll än appen stöder. Appen behöver uppdateras. */
    data class AppTooOld(val protocolVersion: Int) : NodeError

    /** State har inte ett värde per utgång i Info, så värdena kan inte kopplas till utgångarna. */
    data class StateMismatch(val expectedOutputs: Int, val actualValues: Int) : NodeError
}

/** Nodens svar när den avvisar ett kommando. Motsvarar statuskoderna i Result. */
sealed interface CommandError {

    /** Noden känner inte till kommandot. Det är ett fel i appen. */
    data object UnknownCommand : CommandError

    /** Utgången finns inte på noden. Info kan ha ändrats, till exempel efter en firmwareuppdatering. */
    data object InvalidOutput : CommandError

    /** Kommandot passar inte utgångens typ, till exempel PWM till en digital utgång. */
    data object WrongType : CommandError

    /** Kommandot saknar sitt värde. Det är ett fel i appen. */
    data object MissingValue : CommandError

    /** Appen har inte skickat `AUTH` på den här anslutningen. */
    data object NotAuthorized : CommandError

    /** Fel token. Noden kopplar ner, och appen får inte försöka igen med samma token. */
    data object AuthFailed : CommandError

    /** Kommandot var tomt. Det är ett fel i appen. */
    data object BadLength : CommandError

    /** En statuskod som appen inte känner till. Behandlas som ett fel, aldrig som OK. */
    data class Unknown(val status: Int) : CommandError
}
