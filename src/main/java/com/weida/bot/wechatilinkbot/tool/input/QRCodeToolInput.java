package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed QR code generation tool input.
 *
 * @param content URL or text to encode in the QR code
 * @param size    QR code image size in pixels, defaults to 500
 * @author ryx
 * @since 2026-07-24
 */
public record QRCodeToolInput(
        @ToolParameter(description = "要编码为二维码的 URL 或文本内容") String content,
        @ToolParameter(description = "二维码图片尺寸（像素），默认 500", required = false) Integer size) {
}
