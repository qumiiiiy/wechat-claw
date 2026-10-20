package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OcsCompanionReadyRequest(
        @NotBlank @Size(max = 32) String companionVersion,
        @Size(max = 32) String pluginVersion,
        @Size(max = 2_048) String pageUrl) {
}
