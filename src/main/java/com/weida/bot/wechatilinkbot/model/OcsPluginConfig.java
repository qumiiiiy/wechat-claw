package com.weida.bot.wechatilinkbot.model;

/**
 * Minimal configuration to inject into the OCS plugin via the companion script.
 * Each field maps to a Tampermonkey GM_setValue key used by the OCS (ocsjs) plugin.
 */
public record OcsPluginConfig(
        boolean videoReview,
        boolean autoChapterSwitch,
        String playbackRate,
        boolean answerEnabled,
        String questionBankUrl,
        boolean questionBankEnabled) {

    public OcsPluginConfig {
        playbackRate = safeText(playbackRate, "playbackRate", 8);
        questionBankUrl = safeText(questionBankUrl, "questionBankUrl", 2_048);
    }

    private static String safeText(String value, String field, int maxCharacters) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return safeValue;
    }
}
