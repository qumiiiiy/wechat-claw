package com.weida.bot.wechatilinkbot.infrastructure.media;

import com.weida.bot.wechatilinkbot.model.ConvertedVideo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs bounded FFmpeg and ffprobe child processes for speech-video conversion.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class FfmpegMediaConverter {

    private static final Logger LOG = LoggerFactory.getLogger(FfmpegMediaConverter.class);
    private static final long EXECUTABLE_CHECK_TIMEOUT_SECONDS = 5L;
    private static final long MINIMUM_VIDEO_DURATION_MILLIS = 1000L;
    private static final int PROCESS_LOG_MAX_CHARACTERS = 1000;

    private final DashScopeMediaProperties properties;
    private final boolean ffmpegAvailable;
    private final boolean ffprobeAvailable;

    public FfmpegMediaConverter(DashScopeMediaProperties properties) {
        this.properties = properties;
        this.ffmpegAvailable = executableAvailable(properties.getFfmpegExecutable());
        this.ffprobeAvailable = executableAvailable(properties.getFfprobeExecutable());
        logAvailability();
    }

    /**
     * Reports whether FFmpeg can be executed.
     *
     * @return FFmpeg availability
     */
    public boolean isAvailable() {
        return ffmpegAvailable;
    }

    /**
     * Converts provider audio bytes to H.264/AAC MP4.
     *
     * @param audioBytes provider audio payload
     * @param audioFormat provider audio format
     * @param estimatedDurationMillis fallback duration
     * @return converted MP4 payload
     */
    public ConvertedVideo convert(
            byte[] audioBytes,
            String audioFormat,
            long estimatedDurationMillis) {
        validateAudio(audioBytes);
        String extension = safeAudioExtension(audioFormat);
        ConversionFiles files = null;
        try {
            files = createConversionFiles(extension, audioBytes);
            ProcessResult result = run(
                    ffmpegCommand(files.audioFile(), files.videoFile(), extension),
                    files.ffmpegLog());
            verifyConversion(result, files.videoFile());
            long durationMillis = determineDuration(files, estimatedDurationMillis);
            byte[] videoBytes = Files.readAllBytes(files.videoFile());
            validateVideo(videoBytes);
            return new ConvertedVideo(videoBytes, durationMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaConversionException("FFmpeg 转换被中断", exception);
        } catch (IOException exception) {
            throw new MediaConversionException(
                    "无法执行 FFmpeg；请确认服务器已安装 ffmpeg：" + exception.getMessage(),
                    exception);
        } finally {
            deleteConversionFiles(files);
        }
    }

    private ConversionFiles createConversionFiles(String extension, byte[] audioBytes)
            throws IOException {
        Path directory = Files.createTempDirectory("wechat-tts-");
        Path audioFile = directory.resolve("speech." + extension);
        Path videoFile = directory.resolve("speech.mp4");
        Path ffmpegLog = directory.resolve("ffmpeg.log");
        Path ffprobeLog = directory.resolve("ffprobe.log");
        Files.write(audioFile, audioBytes);
        return new ConversionFiles(directory, audioFile, videoFile, ffmpegLog, ffprobeLog);
    }

    private long determineDuration(ConversionFiles files, long estimatedDurationMillis)
            throws IOException, InterruptedException {
        long durationMillis = Math.max(
                estimatedDurationMillis, MINIMUM_VIDEO_DURATION_MILLIS);
        if (!ffprobeAvailable) {
            return durationMillis;
        }
        try {
            ProcessResult probe = run(
                    ffprobeCommand(files.videoFile()), files.ffprobeLog());
            if (probe.exitCode() == 0) {
                return parseDurationMillis(probe.output(), durationMillis);
            }
            LOG.warn(
                    "ffprobe_duration_failed fallbackDurationMs={} detail={}",
                    durationMillis,
                    abbreviate(probe.output(), 300));
        } catch (IOException | MediaConversionException exception) {
            LOG.warn(
                    "ffprobe_unavailable fallbackDurationMs={} detail={}",
                    durationMillis,
                    exception.getMessage());
        }
        return durationMillis;
    }

    private List<String> ffmpegCommand(Path audioFile, Path videoFile, String extension) {
        List<String> command = new ArrayList<>(40);
        command.add(properties.getFfmpegExecutable());
        command.addAll(List.of(
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-f", "lavfi",
                "-i", "color=c=0x111827:s=" + properties.getVideoWidth()
                        + "x" + properties.getVideoHeight() + ":r=2"));
        if ("pcm".equals(extension)) {
            command.addAll(List.of(
                    "-f", "s16le",
                    "-ar", String.valueOf(properties.getSpeechSampleRate()),
                    "-ac", "1"));
        }
        command.addAll(List.of(
                "-i", audioFile.toString(),
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-tune", "stillimage",
                "-threads", "1",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "96k",
                "-ac", "1",
                "-shortest",
                "-movflags", "+faststart",
                videoFile.toString()));
        return command;
    }

    private List<String> ffprobeCommand(Path videoFile) {
        return List.of(
                properties.getFfprobeExecutable(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.toString());
    }

    private ProcessResult run(List<String> command, Path outputFile)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        boolean finished = process.waitFor(
                properties.getConversionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(EXECUTABLE_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new MediaConversionException("媒体转换进程执行超时");
        }
        String output = Files.exists(outputFile)
                ? Files.readString(outputFile, StandardCharsets.UTF_8)
                : "";
        return new ProcessResult(process.exitValue(), output.strip());
    }

    private void verifyConversion(ProcessResult result, Path videoFile) {
        if (result.exitCode() != 0 || !Files.isRegularFile(videoFile)) {
            throw new MediaConversionException(
                    "FFmpeg 转换语音为 MP4 失败："
                            + abbreviate(result.output(), PROCESS_LOG_MAX_CHARACTERS));
        }
    }

    private void validateVideo(byte[] videoBytes) {
        if (videoBytes.length == 0) {
            throw new MediaConversionException("FFmpeg 生成了空 MP4 文件");
        }
        if (videoBytes.length > properties.getMaxDownloadBytes()) {
            throw new MediaConversionException(
                    "生成的 MP4 超过大小限制：" + videoBytes.length + " bytes");
        }
    }

    private static void validateAudio(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new MediaConversionException("待转换的语音文件为空");
        }
    }

    private void logAvailability() {
        if (ffmpegAvailable) {
            LOG.info("ffmpeg_available executable={}", properties.getFfmpegExecutable());
        } else if (properties.isEnabled() && properties.isSpeechEnabled()) {
            LOG.warn(
                    "ffmpeg_unavailable speechToolDisabled=true executable={} configuration=FFMPEG_EXECUTABLE",
                    properties.getFfmpegExecutable());
        }
        if (!ffprobeAvailable && properties.isEnabled() && properties.isSpeechEnabled()) {
            LOG.warn(
                    "ffprobe_unavailable durationFallbackEnabled=true executable={} configuration=FFPROBE_EXECUTABLE",
                    properties.getFfprobeExecutable());
        }
    }

    private static boolean executableAvailable(String executable) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(
                    EXECUTABLE_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String safeAudioExtension(String audioFormat) {
        String normalized = audioFormat == null
                ? "mp3"
                : audioFormat.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mp3", "wav", "opus", "pcm" -> normalized;
            default -> throw new MediaConversionException(
                    "不支持的语音编码格式：" + normalized);
        };
    }

    private static long parseDurationMillis(String output, long fallback) {
        try {
            double seconds = Double.parseDouble(output.strip());
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return fallback;
            }
            return Math.max(
                    MINIMUM_VIDEO_DURATION_MILLIS, Math.round(seconds * 1000.0));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private static void deleteConversionFiles(ConversionFiles files) {
        if (files == null) {
            return;
        }
        deleteQuietly(files.ffprobeLog());
        deleteQuietly(files.ffmpegLog());
        deleteQuietly(files.videoFile());
        deleteQuietly(files.audioFile());
        deleteQuietly(files.directory());
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("temporary_media_delete_failed path={}", path, exception);
            }
        }
    }

    private record ConversionFiles(
            Path directory,
            Path audioFile,
            Path videoFile,
            Path ffmpegLog,
            Path ffprobeLog) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
