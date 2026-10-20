package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Untrusted page result for one previously issued semantic answer command. */
public record OcsAnswerResultRequest(
        @NotBlank @Size(max = 128) String questionId,
        @NotBlank @Size(max = 32) String result,
        @Size(max = 1_000) String content) {
}
