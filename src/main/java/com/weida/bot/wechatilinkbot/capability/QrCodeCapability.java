package com.weida.bot.wechatilinkbot.capability;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Provides user-visible QR code generation through ZXing.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class QrCodeCapability {

    private static final int DEFAULT_SIZE = 500;
    private static final int MIN_SIZE = 100;
    private static final int MAX_SIZE = 2000;

    private static final Map<EncodeHintType, Object> ENCODE_HINTS = Map.of(
            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 2);

    private final QRCodeWriter qrCodeWriter;

    public QrCodeCapability() {
        this.qrCodeWriter = new QRCodeWriter();
    }

    /** Returns whether QR code generation can execute (always true). */
    public boolean available() {
        return true;
    }

    /**
     * Generates a QR code PNG image for the given content.
     *
     * @param content URL or text to encode
     * @param size    image width and height in pixels, null uses default
     * @return generated media containing the QR code PNG
     */
    public GeneratedMedia generateQrCode(String content, Integer size) {
        if (content == null || content.isBlank()) {
            throw new QrCodeGenerationException("二维码内容不能为空");
        }
        int safeSize = resolveSize(size);
        String safeContent = content.strip();
        if (safeContent.length() > 2048) {
            throw new QrCodeGenerationException("二维码内容超过 2048 个字符");
        }
        try {
            byte[] pngBytes = encodeQrCode(safeContent, safeSize);
            return new GeneratedMedia(
                    GeneratedMediaKind.IMAGE,
                    pngBytes,
                    "qrcode.png",
                    0L,
                    "");
        } catch (WriterException exception) {
            throw new QrCodeGenerationException(
                    "二维码编码失败：" + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new QrCodeGenerationException(
                    "二维码图片写入失败：" + exception.getMessage(), exception);
        }
    }

    private byte[] encodeQrCode(String content, int size)
            throws WriterException, IOException {
        BitMatrix matrix = qrCodeWriter.encode(
                content, BarcodeFormat.QR_CODE, size, size, ENCODE_HINTS);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        }
    }

    private static int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        int safeSize = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        if (safeSize != size) {
            throw new QrCodeGenerationException(
                    "二维码尺寸必须在 " + MIN_SIZE + " 到 " + MAX_SIZE + " 像素之间");
        }
        return safeSize;
    }
}
