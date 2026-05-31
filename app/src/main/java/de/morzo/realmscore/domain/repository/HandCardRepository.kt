package de.morzo.realmscore.domain.repository

import kotlinx.coroutines.flow.Flow

data class HandCardEntry(
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?,
)

data class SavedHand(
    val cards: List<HandCardEntry>,
    val totalScore: Int,
)

interface HandCardRepository {
    suspend fun saveHand(
        roundId: String,
        profileId: String,
        cards: List<HandCardEntry>,
        totalScore: Int,
    )

    suspend fun getHand(roundId: String, profileId: String): SavedHand?

    /**
     * For a given round, emits the count of persisted [HandCard]s per profile.
     * Used by RoundEntryScreen to decide whether a player's hand is fully recorded.
     */
    fun observeHandCardCountByProfile(roundId: String): Flow<Map<String, Int>>

    /**
     * Cards already picked by *other* players in the same round. Used by the picker
     * to prevent the same physical card from being selected twice across hands.
     */
    fun observeCardKeysUsedByOtherProfiles(
        roundId: String,
        excludeProfileId: String,
    ): Flow<Set<String>>
}
