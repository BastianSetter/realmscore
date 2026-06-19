package de.morzo.realmscore.di

import android.content.Context
import androidx.room.Room
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.ScannerFactory
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.db.migration.MIGRATION_6_7
import de.morzo.realmscore.data.db.migration.MIGRATION_7_8
import de.morzo.realmscore.data.p2p.BluetoothRfcommManager
import de.morzo.realmscore.data.p2p.CompanionDeviceHelper
import de.morzo.realmscore.data.p2p.HandshakeManager
import de.morzo.realmscore.data.p2p.SessionManager
import de.morzo.realmscore.data.repository.BackupRepositoryImpl
import de.morzo.realmscore.data.repository.DeviceProfileMappingRepositoryImpl
import de.morzo.realmscore.data.repository.GameRepositoryImpl
import de.morzo.realmscore.data.repository.HandCardRepositoryImpl
import de.morzo.realmscore.data.repository.ProfileRepositoryImpl
import de.morzo.realmscore.data.repository.RoundRepositoryImpl
import de.morzo.realmscore.data.repository.SandboxFavoriteRepositoryImpl
import de.morzo.realmscore.data.repository.SettingsRepositoryImpl
import de.morzo.realmscore.data.repository.StatsRepositoryImpl
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.repository.BackupRepository
import de.morzo.realmscore.domain.repository.DeviceProfileMappingRepository
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.repository.SandboxFavoriteRepository
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
            // ⚠️ Phase 24 M3 — TODO before the first public release:
            // This destructive fallback WIPES ALL USER DATA on any schema change without a migration
            // (every version bump so far has dropped the DB). That is acceptable pre-release, but once
            // the app is shipped (versionCode > 1 in users' hands) a single un-migrated schema change
            // would destroy their games/profiles/stats. Replace this with real Migration objects (or
            // Room auto-migrations; exportSchema is already on) and remove the destructive fallback —
            // at minimum gate it behind a debug check. Decision deferred: documenting only for now.
            // Real migrations registered here take precedence over the destructive fallback for the
            // version steps they cover (spec 25.6: 6 → 7 adds the favorite `name` column).
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val deviceUuidProvider: DeviceUuidProvider by lazy {
        DeviceUuidProvider(applicationContext)
    }

    val clock: Clock by lazy { SystemClock() }

    val cardLookup: CardLookup by lazy { CardLookup(applicationContext) }

    // Phase 26 camera scan: the OCR engine is flavour-specific (Tesseract for fdroid, ML Kit for
    // play), built by the active flavour's ScannerFactory and warmed up at app start.
    val cardScanner: CardScanner by lazy { ScannerFactory.create(applicationContext, cardLookup) }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(
            database = database,
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
            database = database,
            roundDao = database.roundDao(),
            roundResultDao = database.roundResultDao(),
            discardCardDao = database.discardCardDao(),
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

    val sandboxFavoriteRepository: SandboxFavoriteRepository by lazy {
        SandboxFavoriteRepositoryImpl(
            dao = database.sandboxFavoriteDao(),
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

    val backupRepository: BackupRepository by lazy {
        BackupRepositoryImpl(
            db = database,
            deviceUuidProvider = deviceUuidProvider,
            clock = clock,
        )
    }

    // Phase 28 P2P sync. Bluetooth RFCOMM transport + QR/code handshake + CompanionDeviceManager
    // association (no ACCESS_FINE_LOCATION). SessionManager is the high-level P2PSessionRepository.
    val bluetoothRfcommManager: BluetoothRfcommManager by lazy {
        BluetoothRfcommManager(applicationContext)
    }

    val handshakeManager: HandshakeManager by lazy { HandshakeManager() }

    val companionDeviceHelper: CompanionDeviceHelper by lazy {
        CompanionDeviceHelper(applicationContext)
    }

    val p2pSessionRepository: P2PSessionRepository by lazy {
        SessionManager(
            bluetooth = bluetoothRfcommManager,
            handshake = handshakeManager,
            deviceUuidProvider = deviceUuidProvider,
        )
    }

    val deviceProfileMappingRepository: DeviceProfileMappingRepository by lazy {
        DeviceProfileMappingRepositoryImpl(
            dao = database.deviceProfileMappingDao(),
            clock = clock,
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
