package com.example.test0.analysis.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import java.util.UUID

interface DailyMarkdownStore {
    fun write(day: LocalDate, markdown: String): String
    fun read(relativePath: String): String?
    fun delete(relativePath: String)
}

class AtomicDailyMarkdownStore(
    context: Context,
) : DailyMarkdownStore {
    private val filesDir = context.applicationContext.filesDir

    override fun write(day: LocalDate, markdown: String): String {
        val relativePath = "analysis/daily/$day-${UUID.randomUUID()}.md"
        val target = File(filesDir, relativePath)
        val directory = checkNotNull(target.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建每日整理目录" }
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(markdown.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
        return relativePath
    }

    override fun read(relativePath: String): String? {
        val file = File(filesDir, relativePath)
        return file.takeIf(File::isFile)?.readText(Charsets.UTF_8)
    }

    override fun delete(relativePath: String) {
        File(filesDir, relativePath).delete()
    }
}
