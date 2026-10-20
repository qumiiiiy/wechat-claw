package com.weida.bot.wechatilinkbot.agent.intent;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit requests for a downloadable text or code file.
 *
 * @author ryx
 * @since 2026-07-22
 */
@Component
public class TextFileIntentDetector {

    private static final Pattern DOCUMENT_FORMAT_PATTERN = Pattern.compile(
            "(?is)(?:\\.pdf\\b|\\.docx\\b|pdf|word)" );

    private static final Pattern INFORMATION_QUERY_PATTERN = Pattern.compile(
            "(?is)(?:如何|怎么|怎样|什么是|教程|方法|原理|是否支持|能否)"
                    + ".{0,24}(?:生成|创建|制作|导出).{0,24}(?:文件|文档)");
    private static final Pattern EXPLICIT_REQUEST_PATTERN = Pattern.compile(
            "(?is)(?:(?:请|帮我|给我|替我|为我|把.{0,40})?"
                    + "(?:生成|创建|制作|整理成|写成|转换成|变成|导出成?).{0,60}"
                    + "(?:文件|文档|\\.(?:txt|md|csv|json|xml|ya?ml|pdf|docx)|"
                    + "PDF|Word|"
                    + "Markdown|代码文件|配置文件)|"
                    + "(?:请)?(?:给我|发我)"
                    + "(?!.{0,10}(?:看|查|找|搜|浏览|列举|列出|显示|展示))"
                    + ".{0,60}(?:文件|文档|Markdown))");

    /** Returns whether the text asks the agent to produce a downloadable file now. */
    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalizedText = text.strip();
        return !INFORMATION_QUERY_PATTERN.matcher(normalizedText).find()
                && EXPLICIT_REQUEST_PATTERN.matcher(normalizedText).find();
    }

    /** Returns whether an explicit file request needs the Word/PDF renderer. */
    public boolean hasDocumentIntent(String text) {
        return hasIntent(text) && DOCUMENT_FORMAT_PATTERN.matcher(text).find();
    }
}
