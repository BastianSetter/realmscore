package de.morzo.realmscore.data.p2p

import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.backup.GameSnapshot
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import de.morzo.realmscore.domain.repository.BackupRepository
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.toScoringChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies inbound P2P sync messages to this device's **local mirror** (Phase 28, Stage B). Every
 * device keeps its own Room copy of the shared game (same UUIDs everywhere); this writer folds the
 * host's broadcasts into that copy, reusing the existing repositories so the on-device data stays a
 * faithful, mergeable subtree.
 *
 * - [applyFullGameState] seeds/refreshes the whole game (initial mirror, reveal, reconnect, end-game).
 * - [applyHandCardUpdate] / [applyDiscardUpdate] fold a single live capture into the mirror; the score
 *   is recomputed locally (per-hand deterministic) rather than trusted off the wire.
 */
class GameMirrorSync(
    private val backupRepository: BackupRepository,
    private val handCardRepo: HandCardRepository,
    private val roundRepo: RoundRepository,
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
) {

    /** Merge a full game snapshot (initial mirror, reveal refresh, reconnect delta, end-of-game). */
    suspend fun applyFullGameState(message: SyncMessage.FullGameState) {
        backupRepository.mergeGame(GameSnapshot(game = message.game, profiles = message.profiles))
    }

    /** Write one player's captured hand into the mirror, recomputing its score the same way capture does. */
    suspend fun applyHandCardUpdate(message: SyncMessage.HandCardUpdate) {
        val entries = message.cards.map {
            HandCardEntry(it.cardKey, it.position, it.jokerTargetCardKey, it.jokerTargetSuit)
        }
        val hand = entries.mapNotNull { cardLookup.getByKey(it.cardKey) }
        if (hand.size != entries.size) return // unknown card key — skip defensively
        // Single canonical scoring path (matches RoundCaptureViewModel.saveHand / reveal re-scoring):
        // every joker/Island/Fountain/Necromancer target is a jokerAssignment keyed by its hand card.
        val choices = entries.toScoringChoices()
        val totalScore = withContext(Dispatchers.Default) {
            engine.score(ScoringInput(hand = hand, jokerAssignments = choices.jokerAssignments)).totalScore
        }
        handCardRepo.saveHand(message.roundId, message.profileId, entries, totalScore)
    }

    /** Write the Mittelfeld (discard pile) into the mirror; this also marks the round's discard scanned. */
    suspend fun applyDiscardUpdate(message: SyncMessage.DiscardUpdate) {
        roundRepo.saveDiscardCards(message.roundId, message.cards)
    }
}
