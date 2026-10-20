package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed translation tool input.
 *
 * @param text          the text to translate
 * @param targetLanguage target language name, e.g. English, Japanese, French
 * @param sourceLanguage optional source language; auto-detected when omitted
 * @author ryx
 * @since 2026-07-24
 */
public record TranslationToolInput(
        @ToolParameter(description = "需要翻译的原文") String text,
        @ToolParameter(description = "目标语言，如：英语、日语、法语、韩语、中文等") String targetLanguage,
        @ToolParameter(required = false, description = "源语言，未指定则自动检测") String sourceLanguage) {
}
