package com.eval.cbm.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalBuildScannerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `fallback scanner preserves nested paths and multiple includes`() {
        File(tempDir, "settings.gradle.kts").writeText(
            """include(
                ":feature:network",
                ":feature:network-api",
                ":core:common",
            )"""
        )

        assertEquals(
            listOf(":feature:network", ":feature:network-api", ":core:common"),
            LocalBuildScanner.scanSettingsText(tempDir).map { it.gradlePath }
        )
    }
}
