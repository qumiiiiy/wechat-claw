package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * Status of the OCS plugin detection and configuration on the browser side.
 */
public record OcsPluginStatus(
        boolean detected,
        String version,
        boolean configApplied,
        List<String> missingKeys) {

    public OcsPluginStatus {
        version = safeText(version, "version", 32);
        missingKeys = missingKeys == null ? List.of() : List.copyOf(missingKeys);
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
