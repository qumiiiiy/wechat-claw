package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;

/** User-visible result of starting a learning-platform login. */
public record LearningLoginResult(String message, GeneratedMedia qrCode) {

    public LearningLoginResult {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.strip();
    }
}
