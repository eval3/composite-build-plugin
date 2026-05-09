package com.eval.cbm.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerListener
import com.eval.cbm.core.CbmProjectService

class CompositeBuildToolWindow : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = CbmProjectService.getInstance(project)
        val panel = ModuleListPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: com.intellij.ui.content.ContentManagerEvent) {
                val component = event.content.component
                if (component is ModuleListPanel) {
                    service.refreshFromStateFile()
                }
            }
        })
    }

    override suspend fun isApplicableAsync(project: Project) = true

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun isApplicable(project: Project) = true
}
