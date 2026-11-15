package io.github.ftatc.tinkertaphome.domain.repository

import io.github.ftatc.tinkertaphome.domain.Outcome
import io.github.ftatc.tinkertaphome.domain.model.AppSettings
import io.github.ftatc.tinkertaphome.domain.model.SettingsError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Fake för tester. Sätt [settings] direkt, eller [updateError] för att få [update] att misslyckas. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    val settings = MutableStateFlow(initial)

    var updateError: SettingsError? = null

    override fun observeSettings(): Flow<AppSettings> = settings

    override suspend fun update(transform: (AppSettings) -> AppSettings): Outcome<Unit, SettingsError> {
        updateError?.let { return Outcome.Failure(it) }
        settings.update(transform)
        return Outcome.Success(Unit)
    }
}
