package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.translation.DashScopeTranslationClient;
import org.springframework.stereotype.Component;

/**
 * Translation capability backed by DashScope model.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class TranslationCapability {

    private final DashScopeTranslationClient translationClient;

    public TranslationCapability(DashScopeTranslationClient translationClient) {
        this.translationClient = translationClient;
    }

    public boolean available() {
        return translationClient.isAvailable();
    }

    public String translate(String text, String targetLanguage, String sourceLanguage) {
        return translationClient.translate(text, targetLanguage, sourceLanguage);
    }
}
