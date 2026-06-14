package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.FavoriteCard
import de.morzo.realmscore.domain.model.SandboxFavorite
import kotlinx.coroutines.flow.Flow

interface SandboxFavoriteRepository {

    /** All saved favorites, ordered ascending by their display [SandboxFavorite.number]. */
    fun observeAll(): Flow<List<SandboxFavorite>>

    suspend fun getById(id: String): SandboxFavorite?

    /**
     * Persists [cards] as the next favorite (number = current max + 1) with an optional free-text
     * [name] and returns the stored favorite, so the caller can keep the star toggle linked to it
     * (spec 25.6) and show the "Saved as favorite #N" confirmation.
     */
    suspend fun save(cards: List<FavoriteCard>, name: String?): SandboxFavorite

    /** Renames a favorite (free text, no uniqueness constraint; null/blank → falls back to number). */
    suspend fun updateName(id: String, name: String?)

    suspend fun delete(id: String)
}
