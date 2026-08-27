package com.eval.cbm.core

import com.intellij.openapi.diagnostic.logger
import com.eval.cbm.model.ModuleConfig
import com.eval.cbm.model.checkLocalDir
import java.io.File

/**
 * 解析和写回 project-repos.json5。
 *
 * 采用正则 + 行扫描策略：
 * - 不依赖第三方 JSON5 库，避免插件体积膨胀
 * - 保留所有注释、空行、原始格式
 * - 仅精确替换目标字段的值
 *
 * JSON5 文件结构（固定格式）：
 * ```
 * {
 *   "repositories": {
 *     "moduleName": {
 *       "url": "...",
 *       "includeBuild": false
 *     }, // comment
 *     ...
 *   }
 * }
 * ```
 */
object Json5ConfigManager {

    private val LOG = logger<Json5ConfigManager>()

    // 匹配模块名键：  "moduleName": {
    private val MODULE_KEY_RE = Regex("""^\s*"([\w-]+)"\s*:\s*\{""")
    // 匹配 url 字段
    private val URL_RE = Regex("""^\s*"url"\s*:\s*"([^"]+)"""")
    // 匹配 path 字段
    private val PATH_RE = Regex("""^\s*"path"\s*:\s*"([^"]+)"""")
    // 匹配 includeBuild 字段
    private val INCLUDE_BUILD_RE = Regex("""^\s*"includeBuild"\s*:\s*(true|false)""")
    private val SUBSTITUTIONS_START_RE = Regex("""^\s*"substitutions"\s*:\s*\[""")
    private val SUB_MODULE_RE = Regex(""""module"\s*:\s*"([^"]+)""")
    private val SUB_PROJECT_RE = Regex(""""project"\s*:\s*"([^"]+)""")
    // 匹配块结束行（考虑 trailing comma）
    private val BLOCK_END_RE = Regex("""^\s*},?\s*(//.+)?$""")
    // 匹配 "repositories": { 行
    private val REPOS_START_RE = Regex("""^\s*"repositories"\s*:\s*\{""")

    /**
     * 从 JSON5 文件加载所有模块配置。
     *
     * @param configFile project-repos.json5 文件
     * @param projectRoot 主工程根目录（用于检测本地目录）
     * @return 按文件顺序排列的模块配置列表
     */
    fun load(configFile: File, projectRoot: File): List<ModuleConfig> {
        if (!configFile.exists()) {
            LOG.warn("Config file not found: ${configFile.absolutePath}")
            return emptyList()
        }

        val lines = configFile.readLines()
        val modules = mutableListOf<ModuleConfig>()

        var inRepositories = false
        var currentName: String? = null
        var currentUrl = ""
        var currentPath: String? = null
        var currentIncludeBuild = false
        var currentSubstitutions = mutableListOf<com.eval.cbm.model.DepSubstitution>()
        var currentSubModule: String? = null
        var currentSubProject: String? = null
        var inSubstitutions = false
        var moduleBraceDepth = 0

        for (line in lines) {
            // 跳过注释行
            val trimmed = line.trim()
            if (trimmed.startsWith("//")) continue

            // 检测进入 repositories 块
            if (!inRepositories && REPOS_START_RE.containsMatchIn(line)) {
                inRepositories = true
                continue
            }

            if (!inRepositories) continue

            // 检测模块键开始
            MODULE_KEY_RE.find(line)?.let { match ->
                currentName = match.groupValues[1]
                currentUrl = ""
                currentPath = null
                currentIncludeBuild = false
                currentSubstitutions = mutableListOf()
                currentSubModule = null
                currentSubProject = null
                inSubstitutions = false
                moduleBraceDepth = 0
                return@let
            }

            // 检测 repositories 块结束（currentName == null 时遇到 } 说明是外层块关闭）
            if (currentName == null) {
                if (BLOCK_END_RE.matches(line)) inRepositories = false
                continue
            }

            // 解析字段
            URL_RE.find(line)?.let { currentUrl = it.groupValues[1] }
            PATH_RE.find(line)?.let { currentPath = it.groupValues[1] }
            INCLUDE_BUILD_RE.find(line)?.let { currentIncludeBuild = it.groupValues[1] == "true" }

            if (SUBSTITUTIONS_START_RE.containsMatchIn(line)) inSubstitutions = true
            if (inSubstitutions) {
                SUB_MODULE_RE.find(line)?.let { currentSubModule = it.groupValues[1] }
                SUB_PROJECT_RE.find(line)?.let { currentSubProject = normalizeProjectPath(it.groupValues[1]) }
                if (currentSubModule != null && currentSubProject != null) {
                    currentSubstitutions += com.eval.cbm.model.DepSubstitution(
                        currentSubModule,
                        currentSubProject
                    )
                    currentSubModule = null
                    currentSubProject = null
                }
                if (line.substringBefore("//").contains(']')) inSubstitutions = false
            }

            moduleBraceDepth += braceDelta(line)

            // 检测模块块结束
            if (moduleBraceDepth == 0) {
                @Suppress("SENSELESS_COMPARISON")
                val name = currentName // currentName 已在前面检查非空
                val localExists = if (currentPath != null) File(currentPath).exists()
                                  else checkLocalDirExists(projectRoot, name)
                modules += ModuleConfig(
                    name = name,
                    url = currentUrl,
                    includeBuild = currentIncludeBuild,
                    localDirExists = localExists,
                    substitutions = sanitizeSubstitutions(name, currentSubstitutions),
                    configPath = currentPath
                )
                currentName = null
            }
        }

        LOG.info("Loaded ${modules.size} modules from ${configFile.name}")
        return modules
    }

    /**
     * 将单个模块的 includeBuild 值写回 JSON5 文件。
     * 精确替换目标模块块内的 "includeBuild" 行，保留所有其他内容不变。
     *
     * @param configFile 目标 JSON5 文件
     * @param moduleName 要修改的模块名
     * @param value      新的 includeBuild 值
     */
    fun setIncludeBuild(configFile: File, moduleName: String, value: Boolean) {
        val lines = configFile.readLines().toMutableList()
        var inTargetModule = false
        var moduleFound = false
        var moduleDepth = 0
        val targetModuleRe = Regex("""^\s*"${Regex.escape(moduleName)}"\s*:\s*\{""")

        for (i in lines.indices) {
            val line = lines[i]

            if (!inTargetModule) {
                if (targetModuleRe.containsMatchIn(line)) {
                    inTargetModule = true
                    moduleFound = true
                    moduleDepth = braceDelta(line)
                }
                continue
            }

            // 在目标模块块内，找 includeBuild 行并替换
            if (INCLUDE_BUILD_RE.containsMatchIn(line)) {
                val newLine = line.replace(
                    Regex("""(includeBuild"\s*:\s*)(true|false)"""),
                    "\$1$value"
                )
                lines[i] = newLine
                LOG.info("Updated $moduleName.includeBuild = $value")
                break
            }

            moduleDepth += braceDelta(line)
            // 遇到目标模块块结束，说明该模块没有 includeBuild 字段
            if (moduleDepth == 0) {
                LOG.warn("Module $moduleName has no includeBuild field")
                break
            }
        }

        if (!moduleFound) {
            LOG.error("Module $moduleName not found in ${configFile.name}")
            return
        }

        configFile.writeText(lines.joinToString("\n"))
    }

    /**
     * 检测模块本地目录是否存在（目录约定：../moduleName_project）
     */
    private fun checkLocalDirExists(projectRoot: File, moduleName: String): Boolean {
        val parentDir = projectRoot.parentFile ?: return false
        return File(parentDir, "${moduleName}_project").exists()
    }

    private fun normalizeProjectPath(path: String): String =
        if (path.startsWith(":")) path else ":$path"

    private fun sanitizeSubstitutions(
        moduleName: String,
        rules: List<com.eval.cbm.model.DepSubstitution>
    ): List<com.eval.cbm.model.DepSubstitution> {
        val seenModules = mutableSetOf<String>()
        return rules.filter { rule ->
            val depParts = rule.dep.split(':')
            val valid = depParts.size == 2 && depParts.all { Regex("""^[\w.-]+$""").matches(it) } &&
                Regex("""^:(?:[\w.-]+)(?::[\w.-]+)*$""").matches(rule.project)
            when {
                !valid -> {
                    LOG.warn("Ignoring invalid substitution in $moduleName: ${rule.dep} -> ${rule.project}")
                    false
                }
                !seenModules.add(rule.dep) -> {
                    LOG.warn("Ignoring duplicate substitution module in $moduleName: ${rule.dep}")
                    false
                }
                else -> true
            }
        }
    }

    private fun braceDelta(line: String): Int {
        var delta = 0
        var quoted = false
        var escaped = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (!quoted && c == '/' && i + 1 < line.length && line[i + 1] == '/') break
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else {
                when (c) {
                    '"' -> quoted = true
                    '{' -> delta++
                    '}' -> delta--
                }
            }
            i++
        }
        return delta
    }
}
