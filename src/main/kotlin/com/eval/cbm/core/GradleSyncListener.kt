package com.eval.cbm.core

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * 管理 Gradle 同步相关的状态保存。
 *
 * 移除了对内部 API 的依赖（com.android.tools.idea.gradle.project.sync）：
 * - GradleSyncState
 * - GradleSyncListenerWithRoot
 *
 * 新的架构：在用户切换模块时立即保存状态文件，无需监听 Gradle 同步事件。
 * 这样更简洁、更稳定，且兼容所有 IDE 版本。
 */
object GradleSyncListener {

    private val LOG = logger<GradleSyncListener>()

    /**
     * 初始化同步监听（当前为空实现，因为状态文件在 setIncludeBuild 时已保存）。
     *
     * @param project 当前 IntelliJ 项目
     * @param onSyncStarted 回调函数（当前未使用）
     */
    fun subscribe(project: Project, onSyncStarted: (String) -> Unit) {
        // 架构更改：状态文件在 CbmProjectService.setIncludeBuild() 时立即保存
        // 不再需要监听 Gradle 同步事件
        LOG.info("GradleSyncListener initialized for project: ${project.name}")
    }
}
