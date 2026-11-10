package com.example.test0.analysis.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyMarkdownStoreTest {
    @Test
    fun eachGenerationUsesAnImmutablePrivateFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AtomicDailyMarkdownStore(context)
        val day = LocalDate.parse("2099-12-30")

        val firstPath = store.write(day, "# 第一版")
        val secondPath = store.write(day, "# 第二版")

        assertNotEquals(firstPath, secondPath)
        assertTrue(firstPath.startsWith("analysis/daily/2099-12-30-"))
        assertEquals("# 第一版", store.read(firstPath))
        assertEquals("# 第二版", store.read(secondPath))
        File(context.filesDir, firstPath).delete()
        File(context.filesDir, secondPath).delete()
    }
}
