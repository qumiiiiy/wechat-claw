package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.TRANSLATE_TEXT;

import com.weida.bot.wechatilinkbot.capability.TranslationCapability;
import com.weida.bot.wechatilinkbot.tool.input.TranslationToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that translates text between languages via DashScope.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Order(60)
@Component
public class TranslationTool implements AgentTool<TranslationToolInput> {

    private final TranslationCapability translationCapability;

    public TranslationTool(TranslationCapability translationCapability) {
        this.translationCapability = translationCapability;
    }

    @Override
    public String name() {
        return TRANSLATE_TEXT;
    }

    @Override
    public String description() {
        return "将文本翻译为指定的目标语言。"
                + "用户要求翻译、中译英、英译中或提及任何语言转换时使用；"
                + "源语言未指定时自动检测。";
    }

    @Override
    public Class<TranslationToolInput> inputType() {
        return TranslationToolInput.class;
    }

    @Override
    public boolean available() {
        return translationCapability.available();
    }

    @Override
    public boolean producesMedia() {
        return false;
    }

    @Override
    public ToolExecution execute(
            TranslationToolInput input,
            ToolContext context) {
        String result = translationCapability.translate(
                input.text(), input.targetLanguage(), input.sourceLanguage());
        return new ToolExecution(result, null);
    }
}
