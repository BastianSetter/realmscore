package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.FavoriteCard
import de.morzo.realmscore.domain.model.SandboxFavorite
import kotlinx.coroutines.flow.Flow

interface SandboxFavoriteRepository {

    /** All saved favorites, ordered ascending by their display [SandboxFavorite.number]. */
    fun observeAll(): Flow<List<SandboxFavorite>>

    suspend fun getById(id: String): SandboxFavorite?

    /**
     * Persists [cards] as the next favorite (number = current max + 1) and returns its number,
     * for the "Saved as favorite #N" confirmation.
     */
    suspend fun save(cards: List<FavoriteCard>): Int

    suspend fun delete(id: String)
}
