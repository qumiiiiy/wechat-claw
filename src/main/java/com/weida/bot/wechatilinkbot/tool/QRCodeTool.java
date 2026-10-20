package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_QR_CODE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.QrCodeCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.QRCodeToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that generates a QR code image from a URL or text.
 *
 * @author PengChengLi
 * @since 2026-07-24
 */
@Order(60)
@Component
public class QRCodeTool implements AgentTool<QRCodeToolInput> {

    private final QrCodeCapability qrCodeCapability;
    private final ObjectMapper objectMapper;

    public QRCodeTool(
            QrCodeCapability qrCodeCapability,
            ObjectMapper objectMapper) {
        this.qrCodeCapability = qrCodeCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_QR_CODE;
    }

    @Override
    public String description() {
        return "根据传入的 URL 或文本内容生成二维码";
    }

    @Override
    public Class<QRCodeToolInput> inputType() {
        return QRCodeToolInput.class;
    }

    @Override
    public boolean available() {
        return qrCodeCapability.available();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return true;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(
            QRCodeToolInput input,
            ToolContext context) {
        GeneratedMedia media = qrCodeCapability.generateQrCode(
                input.content(), input.size());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        result.put("media_type", "qrcode");
        result.put("message", "二维码已生成，将作为微信图片发送");
        return new ToolExecution(result.toString(), media);
    }
}
