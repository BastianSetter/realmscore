package de.morzo.realmscore.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real (non-destructive) schema migrations. The database otherwise still uses a destructive
 * fallback for un-migrated bumps (see [de.morzo.realmscore.di.AppContainer]); migrations listed
 * here take precedence and preserve user data for that specific version step.
 */

/** 6 → 7 (spec 25.6): add the nullable free-text `name` column to sandbox favorites. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sandbox_favorites ADD COLUMN name TEXT")
    }
}

/** 7 → 8 (Phase 28): add the device↔local-profile reconciliation table for P2P sync. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `device_profile_mappings` (" +
                "`deviceId` TEXT NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`deviceId`))",
        )
    }
}
