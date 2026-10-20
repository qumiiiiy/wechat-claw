package com.weida.bot.wechatilinkbot.infrastructure.diagram;

/**
 * Diagram render result.
 *
 * <p>PNG bytes are defensively copied on construction and on every access to
 * prevent downstream mutation.</p>
 *
 * @param pngBytes raw PNG payload
 * @param fileName channel-safe file name with .png extension
 * @author ryx
 * @since 2026-07-24
 */
public record RenderResult(byte[] pngBytes, String fileName) {

    public RenderResult {
        pngBytes = pngBytes.clone();
        fileName = fileName == null ? "" : fileName.strip();
    }

    public byte[] pngBytes() {
        return pngBytes.clone();
    }
}