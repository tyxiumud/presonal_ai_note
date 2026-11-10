package com.example.test0.analysis.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAnalysisDao {
    @Upsert
    suspend fun upsert(entity: DailyAnalysisEntity)

    @Query("SELECT * FROM daily_analyses WHERE dayKey = :dayKey")
    fun observeByDay(dayKey: String): Flow<DailyAnalysisEntity?>

    @Query("SELECT * FROM daily_analyses ORDER BY dayKey DESC")
    fun observeAll(): Flow<List<DailyAnalysisEntity>>

    @Query("SELECT * FROM daily_analyses WHERE dayKey = :dayKey")
    suspend fun getByDay(dayKey: String): DailyAnalysisEntity?

    @Query("DELETE FROM daily_analyses WHERE dayKey = :dayKey")
    suspend fun deleteByDay(dayKey: String)
}
