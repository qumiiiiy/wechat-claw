package com.weida.bot.wechatilinkbot.infrastructure.learning;

import java.time.Instant;
import java.util.Objects;

/** Short-lived Chaoxing QR login challenge kept only in process memory. */
public record ChaoxingLoginChallenge(byte[] qrCodePng, Instant expiresAt) {

    public ChaoxingLoginChallenge {
        if (qrCodePng == null || qrCodePng.length == 0) {
            throw new IllegalArgumentException("qrCodePng must not be empty");
        }
        qrCodePng = qrCodePng.clone();
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public byte[] qrCodePng() {
        return qrCodePng.clone();
    }
}
