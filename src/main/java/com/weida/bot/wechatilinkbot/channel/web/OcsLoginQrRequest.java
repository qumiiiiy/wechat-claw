package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OcsLoginQrRequest(
        @NotBlank @Size(max = 100_000) String qrDataUrl,
        @Size(max = 2_048) String pageUrl) {
}
