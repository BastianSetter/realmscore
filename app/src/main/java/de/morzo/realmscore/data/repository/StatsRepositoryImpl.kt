package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.dao.HandCardDao
import de.morzo.realmscore.data.db.dao.ProfileDao
import de.morzo.realmscore.data.db.dao.RoundDao
import de.morzo.realmscore.data.db.dao.RoundResultDao
import de.morzo.realmscore.domain.model.HandCard
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.profile.MergeResolver
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.toScoringChoices
import de.morzo.realmscore.domain.stats.CardStats
import de.morzo.realmscore.domain.stats.CardStatsRow
import de.morzo.realmscore.domain.stats.CardStatsSort
import de.morzo.realmscore.domain.stats.ClosestRoundInfo
import de.morzo.realmscore.domain.stats.GlobalStats
import de.morzo.realmscore.domain.stats.HeadToHeadStats
import de.morzo.realmscore.domain.stats.PlayerPairInfo
import de.morzo.realmscore.domain.stats.PlayerStats
import de.morzo.realmscore.domain.stats.StatsCalculator
import de.morzo.realmscore.domain.stats.StatsOverview
import de.morzo.realmscore.domain.stats.StatsSnapshot
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StatsRepositoryImpl(
    private val gameDao: GameDao,
    private val roundDao: RoundDao,
    private val roundResultDao: RoundResultDao,
    private val handCardDao: HandCardDao,
    private val profileDao: ProfileDao,
    private val cardLookup: CardLookup,
    private val scoringEngine: ScoringEngine,
) : StatsRepository {

    // --- Phase 24 M1: snapshot cache ---
    // buildSnapshot() reads the whole history and re-scores every hand; the stats screen plus the 7
    // RandomStatProviders would otherwise rebuild it many times per open. We memoise the last snapshot
    // and only rebuild when a cheap fingerprint (counts + max updatedAt over the inputs) changes, i.e.
    // when a game closes, a round/result is added, a score is edited, or a profile is renamed.
    private val snapshotMutex = Mutex()
    private var cachedSnapshot: StatsSnapshot? = null
    private var cachedFingerprint: String? = null

    private suspend fun snapshot(): StatsSnapshot = snapshotMutex.withLock {
        val fingerprint = computeFingerprint()
        cachedSnapshot?.let { if (cachedFingerprint == fingerprint) return@withLock it }
        buildSnapshot().also {
            cachedSnapshot = it
            cachedFingerprint = fingerprint
        }
    }

    private suspend fun computeFingerprint(): String = listOf(
        gameDao.getClosedGameCount(),
        roundDao.getCompletedRoundCount(),
        roundResultDao.getResultCount(),
        roundResultDao.getMaxUpdatedAt() ?: 0L,
        profileDao.getProfileCount(),
        profileDao.getMaxUpdatedAt() ?: 0L,
    ).joinToString("|")

    private suspend fun buildSnapshot(): StatsSnapshot = withContext(Dispatchers.Default) {
        val games = gameDao.getClosedGames().map { it.toDomain() }
        val gameIds = games.map { it.id }

        // --- Phase 3 (Profil-Rework): Merge-/Archiv-Auflösung einmal hier ---
        // Gemergte Profile zählen unter ihrem kanonischen Ziel; archivierte Lineages zählen nirgends.
        // Nach dieser Substitution arbeitet StatsCalculator unverändert auf kanonischen Ids.
        val allProfiles = profileDao.getAll().map { it.toDomain() }
        val profileById = allProfiles.associateBy { it.id }
        fun canonical(id: String): String? = MergeResolver.resolveCanonical(
            id = id,
            mergeTargetOf = { profileById[it]?.mergeTargetId },
            isArchived = { profileById[it]?.isArchived == true },
            isKnown = { profileById.containsKey(it) },
        )
        // Kanonische Endknoten: nicht archiviert, nicht gemergt (inkl. Owner).
        val canonicalProfilesById = allProfiles
            .filter { !it.isArchived && it.mergeTargetId == null }
            .associateBy { it.id }

        val participantsRaw = if (gameIds.isEmpty()) emptyList()
        else gameDao.getParticipantsForGames(gameIds)

        val participantsByGame = participantsRaw
            .groupBy { it.gameId }
            .mapValues { (_, list) ->
                list.sortedBy { it.seatOrder }
                    .mapNotNull { canonical(it.profileId) }
                    .distinct()
                    .mapNotNull { canonicalProfilesById[it] }
            }
        val profilesById: Map<String, Profile> = canonicalProfilesById

        val rounds = if (gameIds.isEmpty()) emptyList()
        else roundDao.getCompletedRoundsForGames(gameIds).map { it.toDomain() }
        val completedRoundsByGame: Map<String, List<Round>> = rounds
            .groupBy { it.gameId }
            .mapValues { (_, list) -> list.sortedBy { it.roundNumber } }

        val roundIds = rounds.map { it.id }
        // Results auf kanonische Ids umschreiben; Results archivierter Lineages fallen ganz raus.
        val results: List<RoundResult> = if (roundIds.isEmpty()) emptyList()
        else roundResultDao.getResultsForRounds(roundIds).map { it.toDomain() }
            .mapNotNull { r ->
                val c = canonical(r.profileId) ?: return@mapNotNull null
                if (c == r.profileId) r else r.copy(profileId = c)
            }
        val resultsByRoundId: Map<String, List<RoundResult>> = results.groupBy { it.roundId }
        val resultsByRoundResultId: Map<String, RoundResult> = results.associateBy { it.id }

        // Nur Handkarten überlebender (nicht-archivierter) Results — archivierte zählen auch in
        // Karten-Statistiken nicht mit.
        val resultIds = results.map { it.id }
        val handCards: List<HandCard> = if (resultIds.isEmpty()) emptyList()
        else handCardDao.getForRoundResults(resultIds).map { it.toDomain() }
        val handCardsByResultId: Map<String, List<HandCard>> = handCards
            .groupBy { it.roundResultId }
            .mapValues { (_, list) -> list.sortedBy { it.position } }

        val cardsByKey = cardLookup.getAll().associateBy { it.key }

        val perCardContribution = computePerCardContributions(handCardsByResultId, cardsByKey)

        StatsSnapshot(
            closedGames = games,
            participantsByGame = participantsByGame,
            completedRoundsByGame = completedRoundsByGame,
            resultsByRoundResultId = resultsByRoundResultId,
            resultsByRoundId = resultsByRoundId,
            handCardsByResultId = handCardsByResultId,
            perCardContributionByResultId = perCardContribution,
            profilesById = profilesById,
            cardsByKey = cardsByKey,
        )
    }

    private fun computePerCardContributions(
        handCardsByResultId: Map<String, List<HandCard>>,
        cardsByKey: Map<String, de.morzo.realmscore.domain.model.CardDefinition>,
    ): Map<String, Map<String, Int>> {
        val result = mutableMapOf<String, Map<String, Int>>()
        for ((rrId, cards) in handCardsByResultId) {
            val hand = cards.mapNotNull { cardsByKey[it.cardKey] }
            if (hand.size != cards.size) continue
            // Use the shared reconstruction so every target (incl. the Necromancer pull) rebuilds as
            // a joker assignment; otherwise the pulled 8th card and its suit-bonus knock-on would be
            // dropped here, making per-card contributions disagree with the stored total (H1/L1).
            val choices = cards.toScoringChoices()
            try {
                val scoring = scoringEngine.score(
                    ScoringInput(
                        hand = hand,
                        jokerAssignments = choices.jokerAssignments,
                    ),
                )
                result[rrId] = scoring.perCard.associate { it.cardKey to it.contributedScore }
            } catch (e: Exception) {
                // If scoring fails for any reason, skip this hand — stats should never crash because
                // of a malformed legacy row. Log it so a real regression doesn't stay invisible (L4).
                Log.w(TAG, "Skipping per-card scoring for round result $rrId", e)
                continue
            }
        }
        return result
    }

    override suspend fun getGlobalStats(): GlobalStats =
        StatsCalculator.computeGlobalStats(snapshot())

    override suspend fun getOverview(): StatsOverview =
        StatsCalculator.computeOverview(snapshot())

    override suspend fun getPlayerStats(profileId: String): PlayerStats? =
        StatsCalculator.computePlayerStats(snapshot(), profileId)

    override suspend fun getCardStats(cardKey: String): CardStats? =
        StatsCalculator.computeCardStats(snapshot(), cardKey)

    override suspend fun getCardStatsOverview(sortBy: CardStatsSort): List<CardStatsRow> =
        StatsCalculator.computeCardStatsOverview(snapshot(), sortBy)

    override suspend fun getHeadToHeadStats(
        profileIdA: String,
        profileIdB: String,
    ): HeadToHeadStats? = StatsCalculator.computeHeadToHead(
        snapshot(),
        profileIdA,
        profileIdB,
    )

    override suspend fun getClosestRoundEver(): ClosestRoundInfo? =
        StatsCalculator.computeClosestRound(snapshot())

    override suspend fun getMostPlayedPair(): PlayerPairInfo? =
        StatsCalculator.computeMostPlayedPair(snapshot())

    private companion object {
        const val TAG = "StatsRepository"
    }
}
