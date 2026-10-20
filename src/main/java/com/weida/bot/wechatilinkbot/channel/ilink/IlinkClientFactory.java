package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.ILinkClientBuilder;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import org.springframework.stereotype.Component;

/**
 * Isolates construction of third-party WeChat iLink SDK clients.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class IlinkClientFactory {

    /**
     * Builds one independent SDK client.
     *
     * @param config shared SDK transport configuration
     * @param resumeContext optional encrypted resume state
     * @param loginListener login callback
     * @param messageListener inbound message callback
     * @return independent client instance
     */
    public ILinkClient create(
            ILinkConfig config,
            ResumeContext resumeContext,
            OnLoginListener loginListener,
            OnMessageListener messageListener) {
        ILinkClientBuilder builder = ILinkClient.builder()
                .config(config)
                .onLogin(loginListener)
                .onMessage(messageListener);
        if (resumeContext != null) {
            builder.resumeContext(resumeContext);
        }
        return builder.build();
    }
}
