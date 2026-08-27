package com.eval.cbm.core

import com.eval.cbm.CbmBundle
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import java.io.File

object LocalBuildScanner {
    data class ProjectEntry(val name: String, val gradlePath: String)
    data class ScanResult(val groupId: String?, val allProjects: List<ProjectEntry>)

    fun validate(buildDir: File): String? {
        if (!buildDir.exists() || !buildDir.isDirectory) return CbmBundle.message("error.dir_not_exist", buildDir.absolutePath)
        val hasSettings = listOf("settings.gradle", "settings.gradle.kts").any { File(buildDir, it).exists() }
        return if (hasSettings) null else CbmBundle.message("error.not_gradle_project")
    }

    /** Uses the Tooling API first and falls back to text scanning. Must run off the EDT. */
    fun scan(buildDir: File): ScanResult {
        val projects = scanWithToolingApi(buildDir).ifEmpty { scanSettingsText(buildDir) }
        return ScanResult(readGroupId(buildDir), projects.sortedBy { it.gradlePath })
    }

    private fun scanWithToolingApi(buildDir: File): List<ProjectEntry> = try {
        GradleConnector.newConnector().forProjectDirectory(buildDir).connect().use { connection ->
            val root = connection.getModel(GradleProject::class.java)
            buildList { collectProjects(root, true, this) }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun collectProjects(project: GradleProject, isRoot: Boolean, result: MutableList<ProjectEntry>) {
        if (!isRoot && !hasApplicationTag(project.projectDirectory)) {
            result += ProjectEntry(project.name, normalizeProjectPath(project.path))
        }
        project.children.forEach { collectProjects(it, false, result) }
    }

    internal fun scanSettingsText(buildDir: File): List<ProjectEntry> {
        val settingsFile = listOf("settings.gradle", "settings.gradle.kts")
            .map { File(buildDir, it) }.firstOrNull { it.exists() } ?: return emptyList()
        val content = settingsFile.readText()
        val withoutComments = content
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lineSequence().joinToString("\n") { it.substringBefore("//") }
        return Regex("""[\"'](:[\w.-]+(?::[\w.-]+)*)[\"']""")
            .findAll(withoutComments)
            .map { normalizeProjectPath(it.groupValues[1]) }
            .distinct()
            .filterNot { hasApplicationTag(projectDirForPath(buildDir, it)) }
            .map { ProjectEntry(it.substringAfterLast(':'), it) }
            .toList()
    }

    private fun normalizeProjectPath(path: String): String = if (path.startsWith(":")) path else ":$path"
    private fun projectDirForPath(buildDir: File, path: String): File =
        path.removePrefix(":").split(':').filter { it.isNotBlank() }.fold(buildDir, ::File)

    private fun hasApplicationTag(moduleDir: File): Boolean {
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml")
        if (!manifest.exists()) return false
        val content = manifest.readText()
        return "<application" in content && "android.intent.category.LAUNCHER" in content
    }

    private fun readGroupId(buildDir: File): String? {
        File(buildDir, "gradle.properties").takeIf { it.exists() }?.readLines()
            ?.firstNotNullOfOrNull { line ->
                Regex("""^\s*[Gg][Rr][Oo][Uu][Pp]\s*=\s*(\S+)""").find(line)?.groupValues?.get(1)?.trim()
            }?.let { return it }
        return listOf("build.gradle.kts", "build.gradle").map { File(buildDir, it) }
            .firstOrNull { it.exists() }?.readLines()
            ?.firstNotNullOfOrNull { line ->
                Regex("""^\s*group\s*=\s*[\"']([^\"']+)[\"']""").find(line)?.groupValues?.get(1)?.trim()
            }
    }
}
