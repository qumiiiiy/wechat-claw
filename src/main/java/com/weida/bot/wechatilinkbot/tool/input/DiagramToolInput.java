package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed diagram tool input.
 *
 * @param diagramType diagram type token (must match a PlantUML allowlist entry)
 * @param source      complete PlantUML source between {@code @startuml} and
 *                    {@code @enduml}
 * @author ryx
 * @since 2026-07-24
 */
public record DiagramToolInput(
        @ToolParameter(description = "图表类型：sequence / class / flowchart / state / activity / usecase / component / deployment / object / timing / erd / gantt / mindmap / wbs / salt 之一")
        String diagramType,
        @ToolParameter(description = "完整 PlantUML 源码，必须以 @startuml 开头、以 @enduml 结尾")
        String source) {
}