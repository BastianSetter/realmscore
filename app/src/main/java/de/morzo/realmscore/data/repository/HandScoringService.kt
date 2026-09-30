package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.cards.CursedItemLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.savedHandScoringInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything beyond the cards that a round's hands are scored against (Phase 30). */
data class RoundScoringContext(
    /** The round's captured Mittelfeld — the Undead score cards in it. */
    val discardPile: List<CardDefinition>,
    val discardScanned: Boolean,
    val playerCount: Int,
    val newSuits: Boolean,
    val cursedItems: Boolean,
)

/**
 * The single canonical way to score and persist a player's hand (Phase 30). Capture, the P2P mirror
 * and the reveal/summary/breakdown screens all go through here, so a hand's stored total always
 * equals what the screens recompute: engine score (with the round's Mittelfeld and the game's player
 * count) plus the points of the cursed items the player used.
 */
class HandScoringService(
    private val engine: ScoringEngine,
    private val cardLookup: CardLookup,
    private val cursedItemLookup: CursedItemLookup,
    private val roundRepo: RoundRepository,
    private val gameRepo: GameRepository,
    private val handCardRepo: HandCardRepository,
) {

    suspend fun context(roundId: String): RoundScoringContext {
        val round = roundRepo.getRoundById(roundId)
        val game = round?.let { gameRepo.getById(it.gameId) }
        val playerCount = round?.let { gameRepo.getParticipants(it.gameId).size } ?: 0
        return RoundScoringContext(
            discardPile = roundRepo.getDiscardCards(roundId).mapNotNull { cardLookup.getByKey(it) },
            discardScanned = round?.discardScanned ?: false,
            playerCount = playerCount,
            newSuits = game?.newSuitsEnabled ?: false,
            cursedItems = game?.cursedItemsEnabled ?: false,
        )
    }

    fun input(entries: List<HandCardEntry>, context: RoundScoringContext): ScoringInput? =
        savedHandScoringInput(entries, cardLookup::getByKey, context.discardPile, context.playerCount)
            ?.copy(newSuits = context.newSuits, discardScanned = context.discardScanned)

    /** Engine result for a saved hand, or null when a card key is unknown. */
    suspend fun score(entries: List<HandCardEntry>, context: RoundScoringContext): ScoringResult? {
        val input = input(entries, context) ?: return null
        return withContext(Dispatchers.Default) { engine.score(input) }
    }

    fun cursedPoints(cursedItemKeys: List<String>, context: RoundScoringContext): Int =
        cursedItemLookup.pointsFor(cursedItemKeys, context.playerCount)

    /** Scores [entries] (+ cursed items) and persists them. Returns false for an unknown card key. */
    suspend fun saveHand(
        roundId: String,
        profileId: String,
        entries: List<HandCardEntry>,
        cursedItemKeys: List<String>,
        context: RoundScoringContext? = null,
    ): Boolean {
        val ctx = context ?: context(roundId)
        val handScore = score(entries, ctx)?.totalScore ?: return false
        val cursedPoints = cursedPoints(cursedItemKeys, ctx)
        handCardRepo.saveHand(
            roundId = roundId,
            profileId = profileId,
            cards = entries,
            totalScore = handScore + cursedPoints,
            cursedItemKeys = cursedItemKeys,
            cursedPoints = cursedPoints,
        )
        return true
    }

    /**
     * Re-scores every saved hand of [roundId] against the current Mittelfeld — called after the
     * Mittelfeld is saved or synced, since the Undead's bonus depends on it (capture order is free).
     * Hands whose total does not change are left untouched.
     */
    suspend fun rescoreRound(roundId: String) {
        val round = roundRepo.getRoundById(roundId) ?: return
        val ctx = context(roundId)
        for (participant in gameRepo.getParticipants(round.gameId)) {
            val saved = handCardRepo.getHand(roundId, participant.profileId) ?: continue
            val handScore = score(saved.cards, ctx)?.totalScore ?: continue
            val cursedPoints = cursedPoints(saved.cursedItemKeys, ctx)
            if (handScore + cursedPoints == saved.totalScore) continue
            handCardRepo.saveHand(
                roundId = roundId,
                profileId = participant.profileId,
                cards = saved.cards,
                totalScore = handScore + cursedPoints,
                cursedItemKeys = saved.cursedItemKeys,
                cursedPoints = cursedPoints,
            )
        }
    }
}
