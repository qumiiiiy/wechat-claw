package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrCodeCapabilityTest {

    private final QrCodeCapability capability = new QrCodeCapability();

    @Test
    void generatesQrCodeWithUrlContent() throws Exception {
        var media = capability.generateQrCode("https://example.com", null);

        assertThat(media.bytes()).isNotEmpty();
        String decoded = decodeQrCode(media.bytes());
        assertThat(decoded).isEqualTo("https://example.com");
    }

    @Test
    void generatesQrCodeWithChineseContent() throws Exception {
        var media = capability.generateQrCode("你好世界", 300);

        assertThat(media.bytes()).isNotEmpty();
        String decoded = decodeQrCode(media.bytes());
        assertThat(decoded).isEqualTo("你好世界");
    }

    @Test
    void generatesQrCodeWithCustomSize() throws Exception {
        var media = capability.generateQrCode("test", 500);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(media.bytes()));
        assertThat(image.getWidth()).isEqualTo(500);
        assertThat(image.getHeight()).isEqualTo(500);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> capability.generateQrCode("", null))
                .isInstanceOf(QrCodeGenerationException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() -> capability.generateQrCode(null, null))
                .isInstanceOf(QrCodeGenerationException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void rejectsOversizedContent() {
        String longContent = "x".repeat(2049);
        assertThatThrownBy(() -> capability.generateQrCode(longContent, null))
                .isInstanceOf(QrCodeGenerationException.class)
                .hasMessageContaining("超过 2048");
    }

    private static String decodeQrCode(byte[] pngBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
