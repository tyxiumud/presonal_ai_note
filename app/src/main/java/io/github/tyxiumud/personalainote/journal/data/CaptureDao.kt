package io.github.tyxiumud.personalainote.journal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert
    suspend fun insert(entity: CaptureEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CaptureEntity>)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT mediaPath FROM captures WHERE id = :id")
    suspend fun mediaPathById(id: Long): String?

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getById(id: Long): CaptureEntity?

    @Query(
        """
        UPDATE captures
        SET textContent = :text, updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateText(id: Long, text: String, updatedAt: Long)

    @Query("UPDATE captures SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String)

    @Query(
        """
        SELECT * FROM captures
        WHERE dayKey = :dayKey
        ORDER BY contentAtEpochMillis DESC, importedAtEpochMillis DESC, id DESC
        """,
    )
    fun observeByDay(dayKey: String): Flow<List<CaptureEntity>>

    @Query(
        """
        SELECT * FROM captures
        WHERE dayKey = :dayKey
        ORDER BY contentAtEpochMillis DESC, importedAtEpochMillis DESC, id DESC
        """,
    )
    suspend fun getByDay(dayKey: String): List<CaptureEntity>

    @Query("SELECT DISTINCT dayKey FROM captures ORDER BY dayKey DESC")
    fun observeDayKeys(): Flow<List<String>>

    /** 跨日期的全部随记，供搜索与标签筛选使用。 */
    @Query("SELECT * FROM captures ORDER BY contentAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<CaptureEntity>>
}
