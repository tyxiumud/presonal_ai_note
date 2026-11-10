package com.example.test0

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPolicyTest {
    @Test
    fun privateJournalIsNotEligibleForAndroidBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backupFlag = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP

        assertEquals(0, backupFlag)
    }
}
