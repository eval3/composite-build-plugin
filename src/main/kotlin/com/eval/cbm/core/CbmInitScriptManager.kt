package com.eval.cbm.core

import com.intellij.openapi.diagnostic.logger
import java.io.File

/**
 * 管理 CBM 的 Gradle 基础设施文件：
 * - ~/.gradle/init.d/cbm.gradle：Gradle init script，自动处理 includeBuild 配置
 * - .idea/cbm/modules.json：工程状态文件（位于项目 .idea 目录下）
 * - .idea/cbm/snapshots.json：分支快照文件
 */
object CbmInitScriptManager {

    private val LOG = logger<CbmInitScriptManager>()

    private const val INIT_SCRIPT_RESOURCE = "/gradle/cbm.init.gradle"
    private const val INIT_SCRIPT_NAME = "cbm.gradle"

    private fun cbmDir(projectRoot: File): File {
        val dir = File(projectRoot, ".idea/cbm")
        dir.mkdirs()
        return dir
    }

    /** 状态文件路径：.idea/cbm/modules.json */
    fun stateFileFor(projectRoot: File): File = File(cbmDir(projectRoot), "modules.json")

    /** 分支快照文件路径：.idea/cbm/snapshots.json */
    fun snapshotFileFor(projectRoot: File): File = File(cbmDir(projectRoot), "snapshots.json")

    /**
     * 将插件 JAR 内的 cbm.init.gradle 部署到 ~/.gradle/init.d/cbm.gradle。
     * 仅在内容变化时写入，避免触发不必要的 Gradle 缓存失效。
     */
    fun deployInitScript() {
        try {
            val resource = CbmInitScriptManager::class.java
                .getResourceAsStream(INIT_SCRIPT_RESOURCE)
                ?: run {
                    LOG.warn("Init script resource not found: $INIT_SCRIPT_RESOURCE")
                    return
                }
            val content = resource.use { it.readBytes() }
            val targetDir = File(System.getProperty("user.home"), ".gradle/init.d")
            targetDir.mkdirs()
            val targetFile = File(targetDir, INIT_SCRIPT_NAME)
            if (!targetFile.exists() || !targetFile.readBytes().contentEquals(content)) {
                targetFile.writeBytes(content)
                LOG.info("Deployed CBM init script to: ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            LOG.error("Failed to deploy CBM init script", e)
        }
    }

    /**
     * 迁移旧版状态文件到新路径，支持三种旧格式：
     * 1. 工程根目录下的 .cbm-include-build.json → .idea/cbm/modules.json
     * 2. ~/.gradle/cbm/<hash>.json → .idea/cbm/modules.json
     * 3. ~/.gradle/cbm/<hash>-snapshots.json → .idea/cbm/snapshots.json
     */
    fun migrateOldStateFile(projectRoot: File) {
        val hash = Integer.toUnsignedString(projectRoot.absolutePath.hashCode(), 16)

        // 迁移旧 state 文件
        val newStateFile = stateFileFor(projectRoot)
        if (!newStateFile.exists()) {
            val oldCandidates = listOf(
                File(projectRoot, ".cbm-include-build.json"),
                File(System.getProperty("user.home"), ".gradle/cbm/${hash}.json")
            )
            oldCandidates.firstOrNull { it.exists() }?.let { oldFile ->
                try {
                    oldFile.copyTo(newStateFile)
                    LOG.info("Migrated state file: ${oldFile.absolutePath} -> ${newStateFile.absolutePath}")
                    oldFile.delete()
                } catch (e: Exception) {
                    LOG.error("Failed to migrate state file: ${oldFile.absolutePath}", e)
                }
            }
        }

        // 迁移旧 snapshot 文件
        val newSnapshotFile = snapshotFileFor(projectRoot)
        if (!newSnapshotFile.exists()) {
            val oldSnapshotFile = File(System.getProperty("user.home"), ".gradle/cbm/${hash}-snapshots.json")
            if (oldSnapshotFile.exists()) {
                try {
                    oldSnapshotFile.copyTo(newSnapshotFile)
                    LOG.info("Migrated snapshot file: ${oldSnapshotFile.absolutePath} -> ${newSnapshotFile.absolutePath}")
                    oldSnapshotFile.delete()
                } catch (e: Exception) {
                    LOG.error("Failed to migrate snapshot file: ${oldSnapshotFile.absolutePath}", e)
                }
            }
        }
    }
}
