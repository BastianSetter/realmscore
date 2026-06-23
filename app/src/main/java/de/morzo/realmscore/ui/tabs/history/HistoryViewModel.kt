package de.morzo.realmscore.ui.tabs.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.ui.util.formatShortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParticipantBadge(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
)

data class WinnerInfo(val name: String, val score: Int)

data class TopStandInfo(val name: String, val score: Int)

enum class HistoryStatus { OPEN, COMPLETED, ABANDONED }

data class HistoryItem(
    val gameId: String,
    val displayName: String?,
    val fallbackName: String,
    val status: HistoryStatus,
    val participants: List<ParticipantBadge>,
    val startedAt: Long,
    val closedAt: Long?,
    val winner: WinnerInfo?,
    val currentTopStand: TopStandInfo?,
)

data class HistoryFilters(
    val statuses: Set<HistoryStatus> = HistoryStatus.values().toSet(),
    val playerProfileIds: Set<String> = emptySet(),
    val searchQuery: String = "",
)

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    val availablePlayers: List<ParticipantBadge> = emptyList(),
    val isLoading: Boolean = true,
)

class HistoryViewModel(
    private val appContext: Context,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    private val rawState: StateFlow<RawHistoryState> = combineTransform(
        gameRepo.observeAllGames(),
        profileRepo.observeAll(),
    ) { games, profiles ->
        val computed = withContext(Dispatchers.Default) {
            buildRawState(games, profiles)
        }
        emit(computed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RawHistoryState.LOADING,
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        rawState,
        _filters,
    ) { raw, f ->
        HistoryUiState(
            items = applyFilters(raw.items, f),
            filters = f,
            availablePlayers = raw.availablePlayers,
            isLoading = raw.isLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setStatusFilter(statuses: Set<HistoryStatus>) {
        _filters.update { it.copy(statuses = statuses) }
    }

    fun toggleStatus(status: HistoryStatus) {
        _filters.update { current ->
            val next = current.statuses.toMutableSet().apply {
                if (!add(status)) remove(status)
            }
            current.copy(statuses = next)
        }
    }

    fun togglePlayerFilter(profileId: String) {
        _filters.update { current ->
            val next = current.playerProfileIds.toMutableSet().apply {
                if (!add(profileId)) remove(profileId)
            }
            current.copy(playerProfileIds = next)
        }
    }

    fun clearPlayerFilter() {
        _filters.update { it.copy(playerProfileIds = emptySet()) }
    }

    fun setSearchQuery(query: String) {
        _filters.update { it.copy(searchQuery = query) }
    }

    /** Marks a running ([HistoryStatus.OPEN]) game as abandoned. */
    fun abandonGame(gameId: String) {
        viewModelScope.launch {
            val game = gameRepo.getById(gameId)
            if (game?.closedAt == null) {
                gameRepo.closeGame(gameId, ClosedReason.ABANDONED)
            }
        }
    }

    private suspend fun buildRawState(
        games: List<Game>,
        profiles: List<Profile>,
    ): RawHistoryState {
        val profilesById = profiles.associateBy { it.id }
        // Profil-Rework: gemergte Profile unter ihrem kanonischen Ziel anzeigen (Namen, Badges, Sieger,
        // Filter). Folgt der mergeTargetId-Kette bis zum Ende; archivierte Profile bleiben hier aber
        // sichtbar (Historie = Anzeige-Kontinuität, nicht Statistik-Zählung).
        fun canonical(id: String): String {
            var cursor = id
            val seen = HashSet<String>()
            var depth = 0
            while (true) {
                if (!seen.add(cursor)) return cursor
                val target = profilesById[cursor]?.mergeTargetId ?: return cursor
                if (!profilesById.containsKey(target)) return cursor
                cursor = target
                if (++depth > 32) return cursor
            }
        }

        val gameIds = games.map { it.id }
        val participantsByGame = gameRepo.getParticipantsForGames(gameIds)
            .groupBy { it.gameId }
        val totalsByGame = roundRepo.getScoreTotalsForGames(gameIds)

        val availablePlayerIds = mutableSetOf<String>()
        val items = games.map { game ->
            val participants = participantsByGame[game.id].orEmpty()
                .sortedBy { it.seatOrder }
                .mapNotNull { entry ->
                    val profile = profilesById[canonical(entry.profileId)] ?: return@mapNotNull null
                    availablePlayerIds += profile.id
                    ParticipantBadge(
                        profileId = profile.id,
                        name = profile.name,
                        colorArgb = profile.colorArgb,
                    )
                }
                .distinctBy { it.profileId }
            // Spiel-Totals auf kanonische Ids umschlüsseln (zwei gemergte Sitze im selben Spiel addieren).
            val totals = totalsByGame[game.id].orEmpty().entries
                .groupBy({ canonical(it.key) }, { it.value })
                .mapValues { (_, scores) -> scores.sum() }
            val status = mapStatus(game)
            val winnerEntry = totals.entries.maxByOrNull { it.value }
            val topName = winnerEntry?.let { profilesById[it.key]?.name }
            val winner = if (status == HistoryStatus.COMPLETED && winnerEntry != null && topName != null) {
                WinnerInfo(topName, winnerEntry.value)
            } else null
            val topStand = if (status == HistoryStatus.OPEN && winnerEntry != null && topName != null && winnerEntry.value > 0) {
                TopStandInfo(topName, winnerEntry.value)
            } else null
            HistoryItem(
                gameId = game.id,
                displayName = game.displayName,
                fallbackName = appContext.getString(
                    R.string.history_fallback_name,
                    formatShortDate(game.startedAt),
                ),
                status = status,
                participants = participants,
                startedAt = game.startedAt,
                closedAt = game.closedAt,
                winner = winner,
                currentTopStand = topStand,
            )
        }

        val availablePlayers = profiles
            .filter { it.id in availablePlayerIds }
            .sortedBy { it.name.lowercase() }
            .map { ParticipantBadge(it.id, it.name, it.colorArgb) }

        return RawHistoryState(
            items = items,
            availablePlayers = availablePlayers,
            isLoading = false,
        )
    }

    private fun mapStatus(game: Game): HistoryStatus = when {
        game.closedAt == null -> HistoryStatus.OPEN
        game.closedReason == ClosedReason.ABANDONED -> HistoryStatus.ABANDONED
        else -> HistoryStatus.COMPLETED
    }

    private fun applyFilters(
        items: List<HistoryItem>,
        filters: HistoryFilters,
    ): List<HistoryItem> {
        val query = filters.searchQuery.trim().lowercase()
        return items.asSequence()
            .filter { it.status in filters.statuses }
            .filter { item ->
                filters.playerProfileIds.isEmpty() ||
                    item.participants.any { p -> p.profileId in filters.playerProfileIds }
            }
            .filter { item ->
                if (query.isEmpty()) return@filter true
                val name = item.displayName ?: item.fallbackName
                name.lowercase().contains(query) ||
                    item.fallbackName.lowercase().contains(query)
            }
            .toList()
    }

    private data class RawHistoryState(
        val items: List<HistoryItem> = emptyList(),
        val availablePlayers: List<ParticipantBadge> = emptyList(),
        val isLoading: Boolean = true,
    ) {
        companion object {
            val LOADING = RawHistoryState(isLoading = true)
        }
    }

    class Factory(
        private val appContext: Context,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val profileRepo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(
                appContext = appContext,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                profileRepo = profileRepo,
            ) as T
        }
    }
}
