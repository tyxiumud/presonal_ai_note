package io.github.tyxiumud.personalainote.journal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.ui.theme.Test0Theme
import java.time.Instant
import java.time.LocalDate

private val previewDate = LocalDate.of(2026, 10, 31)

private val previewCaptures = listOf(
    Capture(
        id = 2,
        text = "下午继续学习 Compose 状态管理",
        contentAt = Instant.parse("2026-10-31T08:20:00Z"),
        importedAt = Instant.parse("2026-11-04T02:00:00Z"),
        updatedAt = Instant.parse("2026-11-04T02:00:00Z"),
        dayKey = previewDate,
    ),
    Capture(
        id = 1,
        text = "补录以前的一段想法",
        contentAt = Instant.parse("2026-10-31T01:10:00Z"),
        importedAt = Instant.parse("2026-11-04T01:50:00Z"),
        updatedAt = Instant.parse("2026-11-04T01:50:00Z"),
        dayKey = previewDate,
    ),
)

@Preview(showBackground = true)
@Composable
fun JournalPreview() {
    Test0Theme {
        JournalScreen(
            state = JournalUiState(
                selectedDate = previewDate,
                captures = previewCaptures,
            ),
            today = LocalDate.of(2026, 11, 4),
            onDraftChanged = {},
            onDateSelected = {},
            onSave = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun JournalDarkPreview() {
    Test0Theme {
        JournalScreen(
            state = JournalUiState(
                selectedDate = previewDate,
                draft = "今天想到……",
                captures = previewCaptures,
            ),
            today = LocalDate.of(2026, 11, 4),
            onDraftChanged = {},
            onDateSelected = {},
            onSave = {},
            onRetry = {},
        )
    }
}
