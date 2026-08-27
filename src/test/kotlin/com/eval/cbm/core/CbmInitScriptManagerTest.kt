package com.eval.cbm.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class CbmInitScriptManagerTest {
    @Test
    fun `prefers IDE configured Gradle User Home`() {
        val actual = CbmInitScriptManager.resolveGradleUserHome(
            configuredPath = "/ide/gradle-home",
            systemPropertyPath = "/system/gradle-home",
            environmentPath = "/env/gradle-home",
            userHome = "/user/home"
        )

        assertEquals(File("/ide/gradle-home"), actual)
    }

    @Test
    fun `falls back through system property environment and default`() {
        assertEquals(
            File("/system/gradle-home"),
            CbmInitScriptManager.resolveGradleUserHome(
                configuredPath = " ",
                systemPropertyPath = "/system/gradle-home",
                environmentPath = "/env/gradle-home",
                userHome = "/user/home"
            )
        )
        assertEquals(
            File("/env/gradle-home"),
            CbmInitScriptManager.resolveGradleUserHome(
                configuredPath = null,
                systemPropertyPath = null,
                environmentPath = "/env/gradle-home",
                userHome = "/user/home"
            )
        )
        assertEquals(
            File("/user/home/.gradle"),
            CbmInitScriptManager.resolveGradleUserHome(
                configuredPath = null,
                systemPropertyPath = null,
                environmentPath = null,
                userHome = "/user/home"
            )
        )
    }

    @Test
    fun `expands tilde in configured path`() {
        val actual = CbmInitScriptManager.resolveGradleUserHome(
            configuredPath = "~/custom-gradle",
            systemPropertyPath = null,
            environmentPath = null,
            userHome = "/user/home"
        )

        assertEquals(File("/user/home/custom-gradle"), actual)
    }
}
