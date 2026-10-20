package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.Size;

public record OcsPluginMissingRequest(
        @Size(max = 300) String reason) {
}
