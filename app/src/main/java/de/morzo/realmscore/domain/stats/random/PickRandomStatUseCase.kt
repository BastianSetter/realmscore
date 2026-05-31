package de.morzo.realmscore.domain.stats.random

import de.morzo.realmscore.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class PickRandomStatUseCase(
    private val providers: List<RandomStatProvider>,
    private val settings: SettingsRepository,
) {
    suspend fun execute(): RandomStatResult {
        val lastKey = settings.lastRandomStatKey.first()
        val candidates = providers
            .filter { it.key != lastKey }
            .mapNotNull { it.provide() }
        if (candidates.isEmpty()) {
            val fallback = providers.mapNotNull { it.provide() }
            if (fallback.isEmpty()) return RandomStatResult.NotEnoughData
            val picked = fallback.random()
            settings.setLastRandomStatKey(picked.key)
            return RandomStatResult.Found(picked)
        }
        val picked = candidates.random()
        settings.setLastRandomStatKey(picked.key)
        return RandomStatResult.Found(picked)
    }
}
