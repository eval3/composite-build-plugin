package com.eval.cbm.core

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

/**
 * 读取 Android Studio 中 :app 模块当前选中的 Build Variant，
 * 并从中提取 productFlavor 名称。
 *
 * 依赖：org.jetbrains.android（Android Studio 内置插件）
 * 核心 API：GradleAndroidModel（AGP 7.4+ / Android Studio Giraffe+）
 *
 * 兼容性说明：
 * IntelliJ 2025.1+ 版本中 GradleAndroidModel 从接口改为类，
 * 该类使用反射来兼容不同版本的 API。
 */
object BuildVariantReader {

    private val LOG = logger<BuildVariantReader>()

    /**
     * 获取 :app 模块当前激活的第一个 flavor 名称（如 "me"、"global"）。
     *
     * 例：selectedVariant = "meOfficialDebug"
     *     productFlavors  = ["me", "official"]
     *     返回 "me"（第一个 flavor 维度，即 artifact 中的标识）
     *
     * @param project 当前 IntelliJ 项目
     * @return flavor 名称，读取失败时返回 null
     */
    fun getActiveFlavor(project: Project): String? {
        val moduleManager = ModuleManager.getInstance(project)

        // 找 app 模块（名称可能是 "app" 或 "projectName.app"）
        val appModule = moduleManager.modules.firstOrNull { module ->
            module.name == "app" || module.name.endsWith(".app")
        }
        if (appModule == null) {
            return null
        }

        val model = GradleAndroidModel.get(appModule)
        if (model == null) {
            return null
        }

        return try {
            // 使用反射兼容不同版本的 GradleAndroidModel API
            // 尝试获取 selectedVariant（新旧版本都可能有）
            val variant = try {
                val method = model::class.java.getDeclaredMethod("getSelectedVariant")
                method.isAccessible = true
                method.invoke(model)
            } catch (e: NoSuchMethodException) {
                // 尝试 getSelectedVariant() 的 Kotlin 属性形式
                val property = model::class.java.getDeclaredMethods()
                    .find { it.name == "getSelectedVariant" || it.name == "selectedVariant" }
                if (property != null) {
                    property.isAccessible = true
                    property.invoke(model)
                } else {
                    return null
                }
            }

            if (variant == null) {
                return null
            }

            // 获取 productFlavors 列表
            @Suppress("UNCHECKED_CAST")
            val productFlavors = try {
                val method = variant::class.java.getDeclaredMethod("getProductFlavors")
                method.isAccessible = true
                method.invoke(variant) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            // 获取 variant name（用于日志）
            val variantName = try {
                val method = variant::class.java.getDeclaredMethod("getName")
                method.isAccessible = true
                method.invoke(variant) as? String ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }

            val flavor = productFlavors.firstOrNull()
            LOG.info("BuildVariantReader: variant=$variantName, productFlavors=$productFlavors, activeFlavor=$flavor")
            flavor
        } catch (e: Exception) {
            LOG.warn("Failed to get selected variant", e)
            null
        }
    }
}
