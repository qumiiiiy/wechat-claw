package com.weida.bot.wechatilinkbot.channel.web;

import java.util.List;

public record OcsPluginConfigAppliedRequest(
        boolean applied,
        List<String> missingKeys) {
}
