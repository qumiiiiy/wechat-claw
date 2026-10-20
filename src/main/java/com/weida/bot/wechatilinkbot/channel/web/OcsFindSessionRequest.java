package com.weida.bot.wechatilinkbot.channel.web;

import jakarta.validation.constraints.Size;

public record OcsFindSessionRequest(
        @Size(max = 256) String hostHint) {
}
