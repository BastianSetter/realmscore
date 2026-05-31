package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun getLocalOwner(): Profile?
    fun observeLocalOwner(): Flow<Profile?>
    suspend fun createOwner(name: String): Profile
    suspend fun updateOwnerName(newName: String): Profile
    suspend fun searchByNamePrefix(prefix: String): List<Profile>
    suspend fun existsByName(name: String): Boolean
    suspend fun createProfile(name: String): Profile
    suspend fun getById(id: String): Profile?
    fun observeAll(): Flow<List<Profile>>
}
