package com.eval.cbm.core

import com.eval.cbm.model.DepSubstitution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class Json5ConfigManagerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `loads explicit substitutions with nested project paths`() {
        val config = File(tempDir, "project-repos.json5").apply {
            writeText(
                """{
                  "repositories": {
                    "network": {
                      "url": "git@example.com:network.git",
                      "substitutions": [
                        {
                          "module": "com.example:network",
                          "project": ":feature:network",
                        },
                        { "module": "com.example:api", "project": "feature:api" },
                        { "module": "com.example:legacy-{flavor}", "project": ":feature:legacy" },
                      ],
                    },
                  },
                }"""
            )
        }

        val module = Json5ConfigManager.load(config, tempDir).single()
        assertEquals(
            listOf(
                DepSubstitution("com.example:network", ":feature:network"),
                DepSubstitution("com.example:api", ":feature:api")
            ),
            module.substitutions
        )
    }
}
