package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaClient;
import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaProperties;
import com.weida.bot.wechatilinkbot.infrastructure.media.FfmpegMediaConverter;
import com.weida.bot.wechatilinkbot.model.ConvertedVideo;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaCapabilityTest {

    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    private HttpServer server;
    private URI baseUri;
    private FfmpegMediaConverter ffmpegConverter;
    private DashScopeMediaClient mediaClient;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext(
                "/generated.png",
                exchange -> bytes(exchange, "image/png", PNG_BYTES));
        server.start();
        ffmpegConverter = mock(FfmpegMediaConverter.class);
        when(ffmpegConverter.isAvailable()).thenReturn(true);
        mediaClient = mock(DashScopeMediaClient.class);
        when(mediaClient.generateImage(any(String.class)))
                .thenReturn(baseUri.resolve("/generated.png"));
        when(mediaClient.synthesizeSpeech(any(String.class), any(String.class)))
                .thenReturn(new byte[] {0x49, 0x44, 0x33, 0x01});
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void generatesAndDownloadsAnImage() throws IOException {
        MediaCapability service = service();

        GeneratedMedia image = service.generateImage("草地上奔跑的柯基");

        assertThat(image.kind()).isEqualTo(GeneratedMediaKind.IMAGE);
        assertThat(image.bytes()).isEqualTo(PNG_BYTES);
        assertThat(image.fileName()).endsWith(".png");
        verify(mediaClient).generateImage("草地上奔跑的柯基");
    }

    @Test
    void synthesizesSpeechAndHandsItToTheMp4Converter() throws IOException {
        byte[] mp4 = new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        when(ffmpegConverter.convert(any(byte[].class), eq("mp3"), anyLong()))
                .thenReturn(new ConvertedVideo(mp4, 3200L));
        MediaCapability service = service();

        GeneratedMedia video = service.generateSpeechVideo(
                "你好，这是语音测试。",
                VoicePreference.FEMALE);

        assertThat(video.kind()).isEqualTo(GeneratedMediaKind.SPEECH_VIDEO);
        assertThat(video.bytes()).isEqualTo(mp4);
        assertThat(video.durationMillis()).isEqualTo(3200L);
        assertThat(video.fileName()).endsWith(".mp4");
        verify(mediaClient).synthesizeSpeech(
                "你好，这是语音测试。",
                "longanhuan_v3");
        verify(ffmpegConverter).convert(any(byte[].class), eq("mp3"), anyLong());
    }

    @Test
    void mapsChildVoicePreferenceToTheConfiguredCosyVoice() throws IOException {
        byte[] mp4 = new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        when(ffmpegConverter.convert(any(byte[].class), eq("mp3"), anyLong()))
                .thenReturn(new ConvertedVideo(mp4, 1800L));

        service().generateSpeechVideo(
                "我是祖国的花朵。",
                VoicePreference.CHILD);

        verify(mediaClient).synthesizeSpeech(
                "我是祖国的花朵。",
                "longhuhu_v3");
    }

    @Test
    void mapsEverySpecializedProfileToItsConfiguredCosyVoice() throws IOException {
        byte[] mp4 = new byte[] {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70};
        when(ffmpegConverter.convert(any(byte[].class), eq("mp3"), anyLong()))
                .thenReturn(new ConvertedVideo(mp4, 1800L));
        MediaCapability service = service();
        Map<VoicePreference, String> expectedVoiceMap = Map.ofEntries(
                Map.entry(VoicePreference.BOY, "longhuhu_v3"),
                Map.entry(VoicePreference.GIRL, "longhuhu_v3"),
                Map.entry(VoicePreference.YOUNG_MALE, "longanlang_v3"),
                Map.entry(VoicePreference.YOUNG_FEMALE, "longanhuan"),
                Map.entry(VoicePreference.MATURE_MALE, "longanzhi_v3"),
                Map.entry(VoicePreference.MATURE_FEMALE, "longanya_v3"),
                Map.entry(VoicePreference.ELDERLY_MALE, "longlaobo_v3"),
                Map.entry(VoicePreference.ELDERLY_FEMALE, "longlaoyi_v3"),
                Map.entry(VoicePreference.ENERGETIC_FEMALE, "longanqin_v3"),
                Map.entry(VoicePreference.SWEET_FEMALE, "longyingxiao_v3"),
                Map.entry(VoicePreference.GENTLE_FEMALE, "longyingtao_v3"),
                Map.entry(VoicePreference.WARM_MALE, "longanyun_v3"),
                Map.entry(VoicePreference.INTELLECTUAL_FEMALE, "longanwen_v3"),
                Map.entry(VoicePreference.NARRATOR_MALE, "longyichen_v3"),
                Map.entry(VoicePreference.NARRATOR_FEMALE, "longwanjun_v3"),
                Map.entry(VoicePreference.PROFESSIONAL_FEMALE, "loongbella_v3"),
                Map.entry(VoicePreference.LIVE_STREAM_FEMALE, "longanxuan_v3"),
                Map.entry(VoicePreference.ROBOT, "longjiqi_v3"),
                Map.entry(VoicePreference.MONKEY_KING, "longhouge_v3"),
                Map.entry(VoicePreference.CANTONESE_MALE, "longanyue_v3"),
                Map.entry(VoicePreference.SHAANXI_MALE, "longshange_v3"),
                Map.entry(VoicePreference.MINNAN_FEMALE, "longanmin_v3"));

        for (Map.Entry<VoicePreference, String> entry
                : expectedVoiceMap.entrySet()) {
            service.generateSpeechVideo("测试音色。", entry.getKey());
            verify(mediaClient).synthesizeSpeech("测试音色。", entry.getValue());
            clearInvocations(mediaClient);
        }
    }

    @Test
    void disablesSpeechToolWhenFfmpegIsUnavailable() {
        when(ffmpegConverter.isAvailable()).thenReturn(false);

        assertThat(service().isSpeechAvailable()).isFalse();
    }

    private MediaCapability service() {
        DashScopeMediaProperties properties = new DashScopeMediaProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(baseUri);
        MediaCapability capability = new MediaCapability(
                properties,
                ffmpegConverter,
                mediaClient);
        return capability;
    }

    private static void bytes(
            HttpExchange exchange,
            String contentType,
            byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
