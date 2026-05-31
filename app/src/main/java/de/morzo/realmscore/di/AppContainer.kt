package de.morzo.realmscore.di

import android.content.Context
import androidx.room.Room
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.repository.GameRepositoryImpl
import de.morzo.realmscore.data.repository.HandCardRepositoryImpl
import de.morzo.realmscore.data.repository.ProfileRepositoryImpl
import de.morzo.realmscore.data.repository.RoundRepositoryImpl
import de.morzo.realmscore.data.repository.SettingsRepositoryImpl
import de.morzo.realmscore.data.repository.StatsRepositoryImpl
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.stats.random.ClosestRoundEverProvider
import de.morzo.realmscore.domain.stats.random.HighestWinRatePlayerProvider
import de.morzo.realmscore.domain.stats.random.MostPlayedTogetherProvider
import de.morzo.realmscore.domain.stats.random.MostPopularCardProvider
import de.morzo.realmscore.domain.stats.random.OwnerAvgScoreProvider
import de.morzo.realmscore.domain.stats.random.OwnerBestHandProvider
import de.morzo.realmscore.domain.stats.random.PickRandomStatUseCase
import de.morzo.realmscore.domain.stats.random.RandomStatProvider
import de.morzo.realmscore.domain.stats.random.RarestPlayedCardProvider
import de.morzo.realmscore.domain.usecase.game.GetGameStateUseCase
import de.morzo.realmscore.domain.usecase.settings.ResetUseCase
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.domain.scoring.rules.BaseGameRules
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.domain.util.Clock
import de.morzo.realmscore.domain.util.SystemClock

class AppContainer(private val applicationContext: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val deviceUuidProvider: DeviceUuidProvider by lazy {
        DeviceUuidProvider(applicationContext)
    }

    val clock: Clock by lazy { SystemClock() }

    val cardLookup: CardLookup by lazy { CardLookup(applicationContext) }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(
            dao = database.profileDao(),
            deviceUuidProvider = deviceUuidProvider,
            clock = clock,
        )
    }

    val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(
            dao = database.gameDao(),
            deviceUuidProvider = deviceUuidProvider,
            clock = clock,
        )
    }

    val roundRepository: RoundRepository by lazy {
        RoundRepositoryImpl(
            roundDao = database.roundDao(),
            roundResultDao = database.roundResultDao(),
            gameDao = database.gameDao(),
            deviceUuidProvider = deviceUuidProvider,
            clock = clock,
        )
    }

    val handCardRepository: HandCardRepository by lazy {
        HandCardRepositoryImpl(
            database = database,
            handCardDao = database.handCardDao(),
            roundResultDao = database.roundResultDao(),
            roundDao = database.roundDao(),
            gameDao = database.gameDao(),
            deviceUuidProvider = deviceUuidProvider,
            clock = clock,
        )
    }

    val getGameStateUseCase: GetGameStateUseCase by lazy {
        GetGameStateUseCase(
            gameRepo = gameRepository,
            roundRepo = roundRepository,
            profileRepo = profileRepository,
        )
    }

    private val cardRuleRegistry by lazy { BaseGameRules.build() }

    private val jokerResolver by lazy {
        JokerResolver { key -> cardLookup.getByKey(key) }
    }

    val scoringEngine: ScoringEngine by lazy {
        ScoringEngine(cardRuleRegistry, jokerResolver) { cardLookup.getByKey(it) }
    }

    val optimalSolver: OptimalSolver by lazy {
        OptimalSolver(scoringEngine, jokerResolver, cardLookup.getAll())
    }

    val statsRepository: StatsRepository by lazy {
        StatsRepositoryImpl(
            gameDao = database.gameDao(),
            roundDao = database.roundDao(),
            roundResultDao = database.roundResultDao(),
            handCardDao = database.handCardDao(),
            profileDao = database.profileDao(),
            cardLookup = cardLookup,
            scoringEngine = scoringEngine,
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(applicationContext)
    }

    val randomStatProviders: List<RandomStatProvider> by lazy {
        listOf(
            OwnerAvgScoreProvider(applicationContext, profileRepository, statsRepository),
            OwnerBestHandProvider(applicationContext, profileRepository, statsRepository),
            HighestWinRatePlayerProvider(applicationContext, statsRepository),
            MostPopularCardProvider(applicationContext, statsRepository),
            RarestPlayedCardProvider(applicationContext, statsRepository),
            ClosestRoundEverProvider(applicationContext, statsRepository),
            MostPlayedTogetherProvider(applicationContext, statsRepository),
        )
    }

    val pickRandomStatUseCase: PickRandomStatUseCase by lazy {
        PickRandomStatUseCase(
            providers = randomStatProviders,
            settings = settingsRepository,
        )
    }

    val resetUseCase: ResetUseCase by lazy {
        ResetUseCase(
            db = database,
            deviceUuidProvider = deviceUuidProvider,
            settings = settingsRepository,
        )
    }
}
