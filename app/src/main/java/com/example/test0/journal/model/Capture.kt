package com.example.test0.journal.model

import java.time.Instant
import java.time.LocalDate

enum class CaptureType {
    TEXT,
    IMAGE,
    AUDIO,
}

data class Capture(
    val id: Long,
    val text: String,
    val contentAt: Instant,
    val importedAt: Instant,
    val updatedAt: Instant,
    val dayKey: LocalDate,
    val type: CaptureType = CaptureType.TEXT,
    /** 应用私有目录下媒体文件的绝对路径；纯文字随记为 null。 */
    val mediaPath: String? = null,
    /** 用户自定义标签，可给文字、图片、语音任意记录添加。 */
    val tags: List<String> = emptyList(),
)

/** 展示用正文：兼容旧版长笔记把标题写成 "# 标题" 的情况，界面里不露出多余的 #。 */
val Capture.displayText: String
    get() = text.removePrefix("# ").trim()
