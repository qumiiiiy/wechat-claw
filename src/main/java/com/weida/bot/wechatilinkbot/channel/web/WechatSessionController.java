package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.channel.WechatChannel;
import com.weida.bot.wechatilinkbot.model.WechatSession;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP channel endpoints for WeChat Bot login-session management.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Validated
@RestController
@RequestMapping("/api/wechat/bots")
public class WechatSessionController {

    private final WechatChannel wechatChannel;

    public WechatSessionController(WechatChannel wechatChannel) {
        this.wechatChannel = wechatChannel;
    }

    /** Lists all managed Bot sessions. */
    @GetMapping
    public List<WechatSessionView> listBots() {
        return wechatChannel.listSessions().stream()
                .map(WechatSessionView::from)
                .toList();
    }

    /** Creates one independent QR-login session. */
    @PostMapping
    public WechatSessionView createBot() {
        return WechatSessionView.from(wechatChannel.createSession());
    }

    /** Gets the selected session's login state. */
    @GetMapping("/{sessionId}/login/status")
    public WechatSessionView getLoginStatus(
            @PathVariable @NotBlank String sessionId) {
        WechatSession session = wechatChannel.getLoginStatus(sessionId);
        return WechatSessionView.from(session);
    }

    /** Returns a non-cacheable PNG QR code for the selected session. */
    @GetMapping(value = "/{sessionId}/login/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(
            @PathVariable @NotBlank String sessionId) throws IOException {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(wechatChannel.getQrCodePng(sessionId));
    }

    /** Refreshes only the selected session's SDK client and QR code. */
    @PostMapping("/{sessionId}/login/refresh")
    public WechatSessionView refreshLogin(
            @PathVariable @NotBlank String sessionId) {
        return WechatSessionView.from(wechatChannel.refreshLogin(sessionId));
    }

    /** Removes and closes the selected session. */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> removeBot(
            @PathVariable @NotBlank String sessionId) {
        wechatChannel.removeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
