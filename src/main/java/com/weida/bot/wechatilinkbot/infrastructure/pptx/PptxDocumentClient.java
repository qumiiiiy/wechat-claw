package com.weida.bot.wechatilinkbot.infrastructure.pptx;

import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Capability-layer PPTX generator. Renders a {@link PresentationRequest}
 * via {@link PptxDocumentRenderer}, lands the bytes inside the configured
 * output directory using the same atomic temp-file + verify + move flow
 * used by {@code WordDocumentClient}, and returns the verified result.
 *
 * <p>The capability owns the output path: callers never supply a
 * filename or directory. The file name is derived from the request title
 * inside the controlled directory.</p>
 */
@Component
public class PptxDocumentClient {

    private static final Logger LOG = LoggerFactory.getLogger(PptxDocumentClient.class);

    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*");

    private final PptxDocumentRenderer renderer;
    private final PptxDocumentProperties properties;
    private final ExecutorService timeoutExecutor;

    @Autowired
    public PptxDocumentClient(
            PptxDocumentRenderer renderer, PptxDocumentProperties properties) {
        this(renderer, properties, Executors.newSingleThreadExecutor(
                new NamedThreadFactory("pptx-write-timeout-")));
    }

    PptxDocumentClient(
            PptxDocumentRenderer renderer,
            PptxDocumentProperties properties,
            ExecutorService timeoutExecutor) {
        this.renderer = Objects.requireNonNull(renderer, "renderer 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor 不能为空");
    }

    @PreDestroy
    public void shutdown() {
        timeoutExecutor.shutdown();
    }

    public boolean isAvailable() {
        return renderer.isAvailable();
    }

    public PresentationGenerationResult generate(PresentationRequest request) {
        if (!isAvailable()) {
            throw new PptxGenerationException("PPTX 渲染未启用");
        }
        Objects.requireNonNull(request, "request 不能为空");

        byte[] bytes = renderer.render(request);
        String safeBaseName = deriveSafeBaseName(request.title());

        Path outputDirectory = properties.getOutputDirectory();
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new PptxGenerationException(
                    "无法创建输出目录：" + outputDirectory, exception);
        }
        Path targetPath = resolveInsideOutput(outputDirectory, safeBaseName);
        Path tempPath;
        try {
            tempPath = Files.createTempFile(targetPath.getParent(),
                    safeBaseName + "-", ".tmp");
        } catch (IOException exception) {
            throw new PptxGenerationException(
                    "无法创建临时文件：" + exception.getMessage(), exception);
        }
        try {
            writeAndVerifyWithTimeout(tempPath, bytes);
            Files.move(tempPath, targetPath,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            byte[] finalBytes = Files.readAllBytes(targetPath);
            byte[] digest = sha256(finalBytes);
            LOG.info(
                    "pptx_generated fileName={} bytes={} sha256={}",
                    targetPath.getFileName(),
                    finalBytes.length,
                    HexFormat.of().formatHex(digest));
            return new PresentationGenerationResult(
                    targetPath, finalBytes, digest);
        } catch (IOException exception) {
            throw new PptxGenerationException(
                    "PPTX 写入失败：" + exception.getMessage(), exception);
        } finally {
            deleteQuietly(tempPath);
        }
    }

    private void writeAndVerifyWithTimeout(Path tempPath, byte[] bytes) {
        long timeoutMillis = properties.getWriteTimeout().toMillis();
        Future<Void> future = timeoutExecutor.submit(() -> {
            Files.write(tempPath, bytes);
            byte[] onDiskBytes = Files.readAllBytes(tempPath);
            if (!hasZipMagic(onDiskBytes)) {
                throw new PptxGenerationException("落盘文件不是有效的 PPTX（ZIP 头缺失）");
            }
            try (InputStream input = Files.newInputStream(tempPath);
                    XMLSlideShow reread = new XMLSlideShow(input)) {
                if (reread.getSlides().isEmpty()) {
                    throw new PptxGenerationException("回读 PPTX 幻灯片数为 0");
                }
            } catch (IOException exception) {
                throw new PptxGenerationException(
                        "无法回读 PPTX：" + exception.getClass().getSimpleName(), exception);
            }
            return null;
        });
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new PptxGenerationException("写入超时（" + timeoutMillis + " ms）");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PptxGenerationException("写入被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof PptxGenerationException pge) {
                throw pge;
            }
            if (cause instanceof IOException io) {
                throw new PptxGenerationException(
                        "写入失败：" + io.getMessage(), io);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new PptxGenerationException("写入失败", cause);
        }
    }

    private static boolean hasZipMagic(byte[] bytes) {
        if (bytes.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (bytes[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOG.warn("pptx_temp_cleanup_failed path={}", path, exception);
        }
    }

    private static String deriveSafeBaseName(String title) {
        String normalized = Normalizer.normalize(title.strip(), Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        String safe = builder.toString();
        if (safe.length() > 48) {
            safe = safe.substring(0, 48);
        }
        if (safe.isEmpty() || Character.isDigit(safe.charAt(0))) {
            safe = "deck-" + safe;
        }
        return safe.toLowerCase(Locale.ROOT);
    }

    private static Path resolveInsideOutput(Path outputDirectory, String baseName) {
        Path candidate = outputDirectory.resolve(baseName + ".pptx");
        Path normalized = candidate.toAbsolutePath().normalize();
        Path directoryNormalized = outputDirectory.toAbsolutePath().normalize();
        if (!normalized.startsWith(directoryNormalized)) {
            throw new PptxGenerationException(
                    "解析后的路径不在输出目录内：" + normalized);
        }
        return normalized;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Outcome of {@link #generate(PresentationRequest)}.
     */
    public record PresentationGenerationResult(
            Path filePath,
            byte[] bytes,
            byte[] sha256) {

        public PresentationGenerationResult {
            Objects.requireNonNull(filePath, "filePath 不能为空");
            Objects.requireNonNull(bytes, "bytes 不能为空");
            Objects.requireNonNull(sha256, "sha256 不能为空");
            bytes = bytes.clone();
            sha256 = sha256.clone();
        }

        public String sha256Hex() {
            return HexFormat.of().formatHex(sha256);
        }

        public String fileName() {
            return filePath.getFileName().toString();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }
    }
}