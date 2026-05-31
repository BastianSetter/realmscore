package de.morzo.realmscore.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.dao.HandCardDao
import de.morzo.realmscore.data.db.dao.ProfileDao
import de.morzo.realmscore.data.db.dao.RoundDao
import de.morzo.realmscore.data.db.dao.RoundResultDao
import de.morzo.realmscore.data.db.entity.GameEntity
import de.morzo.realmscore.data.db.entity.GameParticipantEntity
import de.morzo.realmscore.data.db.entity.HandCardEntity
import de.morzo.realmscore.data.db.entity.ProfileEntity
import de.morzo.realmscore.data.db.entity.RoundEntity
import de.morzo.realmscore.data.db.entity.RoundResultEntity

@Database(
    entities = [
        ProfileEntity::class,
        GameEntity::class,
        GameParticipantEntity::class,
        RoundEntity::class,
        RoundResultEntity::class,
        HandCardEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun gameDao(): GameDao
    abstract fun roundDao(): RoundDao
    abstract fun roundResultDao(): RoundResultDao
    abstract fun handCardDao(): HandCardDao

    companion object {
        const val DB_NAME = "fantasy_realms.db"
    }
}
