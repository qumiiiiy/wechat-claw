package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Short-lived QR code image captured from the Chaoxing login page,
 * relayed to the user's phone for scanning.
 */
public record OcsLoginQrRelay(
        String qrDataUrl,
        Instant expiresAt) {

    private static final int MAX_DATA_URL_CHARACTERS = 100_000;

    public OcsLoginQrRelay {
        if (qrDataUrl == null || qrDataUrl.isBlank()) {
            throw new IllegalArgumentException("qrDataUrl must not be blank");
        }
        qrDataUrl = qrDataUrl.strip();
        if (qrDataUrl.length() > MAX_DATA_URL_CHARACTERS) {
            throw new IllegalArgumentException("qrDataUrl is too large");
        }
        if (!qrDataUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException("qrDataUrl must be a data URL");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
