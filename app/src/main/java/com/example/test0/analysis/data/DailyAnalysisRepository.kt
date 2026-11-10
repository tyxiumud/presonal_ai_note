package com.example.test0.analysis.data

import com.example.test0.analysis.model.DailyAnalysis
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface DailyAnalysisRepository {
    fun observeByDay(day: LocalDate): Flow<DailyAnalysis?>

    /** 全部整理结果，按日期倒序；供阅览页做成一条一条的条目。 */
    fun observeAll(): Flow<List<DailyAnalysis>> = flowOf(emptyList())

    suspend fun save(analysis: DailyAnalysis): DailyAnalysis

    /** 删除某一天的整理结果和对应 Markdown，用于原始记录被删除后的清理。 */
    suspend fun deleteByDay(day: LocalDate)
}
