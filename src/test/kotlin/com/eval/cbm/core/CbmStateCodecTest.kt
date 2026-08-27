package com.eval.cbm.core

import com.eval.cbm.model.DepSubstitution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CbmStateCodecTest {
    @Test
    fun `round trips structured substitutions and escaped paths`() {
        val state = CbmState(
            modules = listOf(
                CbmStateModule(
                    name = "network",
                    path = "C:\\work\\network\"project",
                    substitutions = listOf(
                        DepSubstitution("com.example:network", ":feature:network")
                    )
                )
            ),
            updatedAt = "now"
        )

        assertEquals(state, CbmStateCodec.decode(CbmStateCodec.encode(state)))
    }

    @Test
    fun `reads legacy enabledModules and compact deps`() {
        val state = CbmStateCodec.decode(
            """{
              "groupId": "legacy.group",
              "activeFlavor": "legacy",
              "enabledModules": ["network"],
              "customModules": [
                {"name":"local","path":"/tmp/local","flavorAware":true,"deps":"com.example:local=:feature:local"}
              ]
            }"""
        )

        assertEquals(listOf("network"), state.modules.map { it.name })
        assertEquals(
            listOf(DepSubstitution("com.example:local", ":feature:local")),
            state.customModules.single().substitutions
        )
    }
}
