package com.weida.bot.wechatilinkbot.channel.ilink;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Renders QR content as an in-memory PNG image.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class QrCodeRenderer {

    /**
     * Generates a square PNG QR code.
     *
     * @param content QR payload
     * @param size image width and height
     * @return PNG bytes
     * @throws WriterException when the payload cannot be encoded
     * @throws IOException when PNG writing fails
     */
    public byte[] generatePng(String content, int size) throws WriterException, IOException {
        BitMatrix matrix = new QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        }
    }
}
