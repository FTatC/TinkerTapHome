package io.github.ftatc.tinkertaphome.domain.model

import java.util.UUID

/**
 * Pekar ut en characteristic på en enhet: tjänsten den ligger i och dess egen UUID.
 *
 * Vilka UUID:er en enhet har bestäms av enhetsprotokollet och ligger inte här.
 */
data class CharacteristicId(val service: UUID, val characteristic: UUID)
