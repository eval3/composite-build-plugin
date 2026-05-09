package com.eval.cbm.mcp

import com.eval.cbm.core.CbmProjectService
import com.eval.cbm.model.ModuleStatus
import com.eval.cbm.model.resolveLocalDir
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.project.ProjectManager
import kotlinx.serialization.Serializable
import java.io.File

class CbmToolset : McpToolset {

    @McpTool
    @McpDescription(description = "Returns the list of modules currently composite-built (LOCAL status) in the Android project, including each module's name and local source path.")
    suspend fun get_composite_build_modules(): CompositeBuildResult {
        val allProjects = ProjectManager.getInstance().openProjects
        val project = allProjects.firstOrNull { p ->
            CbmProjectService.getInstance(p).modules.isNotEmpty()
        } ?: allProjects.firstOrNull()
        ?: return CompositeBuildResult(modules = emptyList(), error = "No open project found")

        val service = CbmProjectService.getInstance(project)
        val modules = service.modules

        if (modules.isEmpty()) {
            return CompositeBuildResult(
                modules = emptyList(),
                error = "Module list not loaded. Please open the Composite Build panel first."
            )
        }

        val projectRoot = File(project.basePath ?: "")
        val localModules = modules
            .filter { it.status == ModuleStatus.LOCAL }
            .map { module ->
                LocalModule(
                    name = module.name,
                    localPath = module.resolveLocalDir(projectRoot)?.absolutePath ?: ""
                )
            }

        return CompositeBuildResult(modules = localModules)
    }
}

@Serializable
data class CompositeBuildResult(
    val modules: List<LocalModule>,
    val error: String? = null
)

@Serializable
data class LocalModule(
    val name: String,
    val localPath: String
)
