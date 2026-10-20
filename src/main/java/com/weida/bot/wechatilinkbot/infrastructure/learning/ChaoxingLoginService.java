package com.weida.bot.wechatilinkbot.infrastructure.learning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Starts an in-memory Chaoxing QR login session shared with course queries. */
@Component
public class ChaoxingLoginService {

    private static final Logger log = LoggerFactory.getLogger(ChaoxingLoginService.class);

    private final ChaoxingProperties properties;
    private final ChaoxingClient chaoxingClient;

    public ChaoxingLoginService(
            ChaoxingProperties properties,
            ChaoxingClient chaoxingClient) {
        this.properties = properties;
        this.chaoxingClient = chaoxingClient;
    }

    /** Returns a short-lived QR challenge without writing it or any cookie to disk. */
    public ChaoxingLoginChallenge login() {
        if (!properties.isEnabled()) {
            throw new ChaoxingLoginException("学习通功能未启用，请先设置 CHAOXING_ENABLED=true。");
        }
        try {
            return chaoxingClient.startQrLogin();
        } catch (ChaoxingLoginException exception) {
            log.warn("chaoxing_qr_login_start_failed code=upstream_or_page_changed");
            throw exception;
        }
    }
}
