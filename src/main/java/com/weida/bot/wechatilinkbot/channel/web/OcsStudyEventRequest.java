package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Untrusted event reported by the local OCS companion script. */
public record OcsStudyEventRequest(
        @NotBlank
        @Size(max = 32)
        String eventType,
        @Size(max = 300)
        String lessonTitle,
        @Size(max = 20_000)
        String content,
        @Min(0)
        @Max(100)
        Integer progressPercent) {
}
