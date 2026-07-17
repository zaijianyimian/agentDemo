/**
 * Markdown 技能模块。
 *
 * <p>从 Markdown 文件加载技能定义、维护技能与工具的映射，并在调用时把模板渲染成最终 prompt。</p>
 */
@ApplicationModule(
        id = "markdownskill",
        displayName = "MarkdownSkill",
        allowedDependencies = {
                "model::application",
                "shared::*"
        }
)
package com.example.demo.markdownskill;

import org.springframework.modulith.ApplicationModule;