package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates untrusted PlantUML source text before it is handed to the CLI.
 *
 * <p>Rejects missing {@code @startuml}/{@code @enduml} markers, any
 * preprocessor directive starting with {@code !} (which would otherwise
 * enable {@code !include} / {@code !includeurl} / {@code !import} file
 * loading and remote URL fetches), sprite includes, and anything that
 * exceeds the configured character cap.</p>
 *
 * <p>This guard performs only validation; it never mutates the source.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
public final class PlantumlSourceGuard {

    private static final Pattern PREPROCESSOR_DIRECTIVE =
            Pattern.compile("(?m)^\\s*!\\w+.*$");
    private static final Pattern SPRITE_INCLUDE =
            Pattern.compile("\\{\\{.*?\\}\\}");
    /**
     * Any PlantUML opening marker — not just {@code @startuml}. Mind maps use
     * {@code @startmindmap}, WBS uses {@code @startwbs}, etc., and the model
     * legitimately picks the right one based on diagram type. Restricting to
     * {@code @startuml} alone would reject every non-default diagram.
     */
    private static final Pattern START_MARKER = Pattern.compile("@start\\w+");
    /**
     * Any PlantUML closing marker, mirroring the opening-marker policy.
     */
    private static final Pattern END_MARKER = Pattern.compile("@end\\w+");

    private PlantumlSourceGuard() {
    }

    /**
     * Validates the diagram type and source against the configured policy.
     *
     * @param diagramType user-provided type token
     * @param source user-provided PlantUML source text
     * @param properties configuration controlling limits and the type allowlist
     * @throws DiagramException when any rule fails
     */
    public static void validate(String diagramType, String source, DiagramProperties properties) {
        if (properties == null) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_DISABLED, "Diagram 配置不可用");
        }
        if (!properties.isEnabled()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_DISABLED, "图表工具未启用");
        }
        validateType(diagramType, properties.getAllowedDiagramTypes());
        validateSource(source, properties.getMaxSourceCharacters());
    }

    private static void validateType(String diagramType, List<String> allowed) {
        if (diagramType == null) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_INVALID_TYPE, "图表类型不能为空");
        }
        String normalized = diagramType.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_INVALID_TYPE, "图表类型不能为空");
        }
        for (String candidate : allowed) {
            if (candidate != null && normalized.equals(candidate.strip().toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        throw new DiagramException(
                DiagramErrorCode.DIAGRAM_INVALID_TYPE,
                "不支持的图表类型：" + diagramType);
    }

    private static void validateSource(String source, int maxCharacters) {
        if (source == null || source.strip().isEmpty()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_SOURCE_REQUIRED, "图表源码不能为空");
        }
        if (source.length() > maxCharacters) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_SOURCE_TOO_LONG,
                    "图表源码超过字符上限：" + source.length() + " > " + maxCharacters);
        }
        if (!START_MARKER.matcher(source).find()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE,
                    "图表源码缺少 @start... 起标记（如 @startuml/@startmindmap）");
        }
        if (!END_MARKER.matcher(source).find()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE,
                    "图表源码缺少 @end... 止标记（如 @enduml/@endmindmap）");
        }
        if (PREPROCESSOR_DIRECTIVE.matcher(source).find()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE,
                    "图表源码包含 PlantUML 前处理指令 (!include / !import / !pragma 等)，已拒绝");
        }
        if (SPRITE_INCLUDE.matcher(source).find()) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE,
                    "图表源码包含精灵包含 {{...}}，已拒绝");
        }
    }
}