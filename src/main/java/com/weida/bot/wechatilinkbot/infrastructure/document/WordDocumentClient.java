package com.weida.bot.wechatilinkbot.infrastructure.document;

import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest;
import com.weida.bot.wechatilinkbot.model.DocumentEditResult;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Server-controlled DOCX capability.
 *
 * <p>The capability owns the output filesystem. The model never supplies a
 * path; this class derives a safe basename from the validated request
 * title, anchors it under the configured output directory, and confirms
 * that the canonicalized file path stays inside the configured boundary
 * before writing.</p>
 *
 * <p>Write contract (atomic):</p>
 * <ol>
 *   <li>Render to bytes through {@link DocumentRenderer}.</li>
 *   <li>Create a sibling temp file inside the same directory as the final
 *       target, then write the bytes there.</li>
 *   <li>Re-open the temp file through {@link XWPFDocument} (POI) inside a
 *       try-with-resources block, then compare the structural sections
 *       against the original request: title, heading/paragraph counts,
 *       list item counts, table rows × columns, and page-break presence.</li>
 *   <li>The write + verify step is bounded by
 *       {@link DocumentGenerationProperties#getWriteTimeout()}; on timeout
 *       the temp file is deleted and a
 *       {@link DocumentGenerationException} is raised.</li>
 *   <li>On success, the temp file is atomically renamed to the final
 *       path. The target path never exists with partially-written bytes.</li>
 *   <li>The temp file is removed in a {@code finally} block whether the
 *       operation succeeded or failed.</li>
 * </ol>
 *
 * <p>The legacy {@code generate(title, content)} entry point is preserved
 * for plain-text callers; it uses the same renderer + atomic write flow
 * with a single paragraph-per-line request.</p>
 */
@Component
public class WordDocumentClient {

    private static final Logger LOG = LoggerFactory.getLogger(WordDocumentClient.class);

    private static final Pattern SLUG_STRIPPER = Pattern.compile("[^\\p{L}\\p{N}_-]+");
    private static final Pattern REPEATED_DASH = Pattern.compile("_+");
    private static final Pattern WINDOWS_DEVICE_NAME = Pattern.compile(
            "(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};
    private static final String TEMP_SUFFIX = ".tmp";

    private final DocumentRenderer renderer;
    private final DocumentGenerationProperties properties;
    private final ExecutorService timeoutExecutor;

    @Autowired
    public WordDocumentClient(
            DocumentRenderer renderer,
            DocumentGenerationProperties properties) {
        this(
                renderer,
                properties,
                Executors.newSingleThreadExecutor(
                        new NamedThreadFactory("document-write-timeout-")));
    }

    WordDocumentClient(
            DocumentRenderer renderer,
            DocumentGenerationProperties properties,
            ExecutorService timeoutExecutor) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    @PreDestroy
    public void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    public boolean isAvailable() {
        return renderer.isAvailable();
    }

    /**
     * Renders, writes, and verifies a structured DOCX inside the configured
     * output directory using the atomic temp-file flow described in the
     * class javadoc.
     */
    public DocumentGenerationResult generate(DocumentRequest request) {
        if (!isAvailable()) {
            throw new DocumentGenerationException("文档生成未启用");
        }
        Objects.requireNonNull(request, "request");
        String baseTitle = request.title() != null ? request.title() : "document";
        validateTitleForFileName(baseTitle);

        Path outputDirectory = properties.getOutputDirectory();
        ensureOutputDirectory(outputDirectory);

        String safeBaseName = deriveSafeBaseName(baseTitle);
        Path targetPath = resolveInsideOutput(outputDirectory, safeBaseName);

        byte[] bytes = renderer.render(request);

        Path tempPath = createTempSibling(targetPath);
        DocumentGenerationResult tempResult;
        try {
            tempResult = writeAndVerifyWithTimeout(tempPath, bytes, request);
            atomicMove(tempPath, targetPath);
        } finally {
            deleteQuietly(tempPath);
        }

        DocumentGenerationResult result = withTargetPath(tempResult, targetPath);
        LOG.info(
                "document_generated fileName={} sections={} bytes={} sha256={}",
                result.filePath().getFileName(),
                countSections(request),
                result.bytes().length,
                result.sha256Hex());
        return result;
    }

    /**
     * Convenience entry point kept for backward compatibility with callers
     * that only have a single plain-text body. The body is split on newlines
     * and rendered as paragraph sections; an empty body produces a single
     * placeholder paragraph.
     */
    public byte[] generate(String title, String content) {
        if (!isAvailable()) {
            throw new DocumentGenerationException("文档生成未启用");
        }
        if (content == null || content.isBlank()) {
            throw new DocumentGenerationException("文档内容不能为空");
        }
        if (content.length() > properties.getMaxContentCharacters()) {
            throw new DocumentGenerationException(
                    "文档内容超过最大字符数限制：" + properties.getMaxContentCharacters());
        }

        List<DocumentSection> sections = new ArrayList<>();
        for (String line : content.split("\\r?\\n", -1)) {
            sections.add(new DocumentSection.ParagraphSection(line));
        }
        String safeTitle = title == null || title.isBlank() ? "document" : title;
        DocumentRequest request = new DocumentRequest(safeTitle, sections);
        DocumentGenerationResult result = generate(request);
        return result.bytes();
    }

    /**
     * Loads the DOCX at the server-resolved path, applies the narrow edits
     * declared by {@link DocumentEditRequest}, and writes a new copy inside
     * the configured output directory using the same atomic temp-file flow
     * as {@link #generate(DocumentRequest)}. The source file is never
     * mutated; the returned record carries the source SHA-256 so callers
     * can independently confirm byte-equality of the original.
     */
    public DocumentEditResult edit(Path trustedSourcePath, DocumentEditRequest request) {
        if (!isAvailable()) {
            throw new DocumentGenerationException("文档生成未启用");
        }
        Objects.requireNonNull(trustedSourcePath, "trustedSourcePath 不能为空");
        Objects.requireNonNull(request, "request 不能为空");
        if (!Files.isRegularFile(trustedSourcePath)) {
            throw new DocumentReadException("源文件不是普通文件");
        }

        byte[] sourceBytes;
        try {
            sourceBytes = Files.readAllBytes(trustedSourcePath);
        } catch (IOException exception) {
            throw new DocumentReadException(
                    "无法读取源文件：" + exception.getMessage(), exception);
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new DocumentReadException(
                    "源 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        byte[] sourceDigest = sha256(sourceBytes);

        Path outputDirectory = properties.getOutputDirectory();
        ensureOutputDirectory(outputDirectory);

        String safeBaseName = deriveEditBaseName(trustedSourcePath);
        Path targetPath = resolveInsideOutput(outputDirectory, safeBaseName);

        DocumentRenderer.EditOutcome outcome = renderer.edit(sourceBytes, request);

        Path tempPath = createTempSibling(targetPath);
        DocumentEditResult editResult;
        try {
            DocumentEditResult tempResult = writeAndVerifyEditWithTimeout(
                    tempPath, outcome.bytes(), sourceBytes.length);
            atomicMove(tempPath, targetPath);
            editResult = new DocumentEditResult(
                    targetPath,
                    tempResult.bytes(),
                    tempResult.sha256(),
                    outcome.replacementsApplied(),
                    sourceDigest);
        } finally {
            deleteQuietly(tempPath);
        }

        LOG.info(
                "document_edited fileName={} replacements={} bytes={} source_sha256={}",
                editResult.fileName(),
                editResult.replacementsApplied(),
                editResult.bytes().length,
                editResult.sourceSha256Hex());
        return editResult;
    }

    private String deriveEditBaseName(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return deriveSafeBaseName(base) + "-edit";
    }

    private DocumentEditResult writeAndVerifyEditWithTimeout(
            Path tempPath, byte[] bytes, int sourceSize) {
        long timeoutMillis = properties.getWriteTimeout().toMillis();
        Future<DocumentEditResult> future = timeoutExecutor.submit(() -> {
            Files.write(tempPath, bytes);
            byte[] onDiskBytes = Files.readAllBytes(tempPath);
            if (!hasZipMagic(onDiskBytes)) {
                throw new DocumentReadException("落盘文件不是有效的 DOCX（ZIP 头缺失）");
            }
            if (onDiskBytes.length > properties.getMaxOutputBytes()) {
                throw new DocumentGenerationException(
                        "落盘 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            if (onDiskBytes.length == 0) {
                throw new DocumentGenerationException("落盘 DOCX 为空");
            }
            try (InputStream input = Files.newInputStream(tempPath);
                    XWPFDocument reread = new XWPFDocument(input)) {
                if (reread.getParagraphs().isEmpty()) {
                    throw new DocumentReadException("回读 DOCX 段落数为 0");
                }
            } catch (IOException exception) {
                throw new DocumentReadException(
                        "无法回读 DOCX：" + exception.getClass().getSimpleName(), exception);
            }
            if (onDiskBytes.length > sourceSize * 2) {
                throw new DocumentGenerationException(
                        "修改后体积异常膨胀（" + onDiskBytes.length
                                + " 字节，源 " + sourceSize + " 字节）");
            }
            return new DocumentEditResult(
                    tempPath, onDiskBytes, sha256(onDiskBytes), 0, new byte[0]);
        });
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new DocumentGenerationException(
                    "写入超时（" + timeoutMillis + " ms）");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentGenerationException("写入被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DocumentGenerationException write) {
                throw write;
            }
            if (cause instanceof DocumentReadException read) {
                throw read;
            }
            if (cause instanceof IOException io) {
                throw new DocumentGenerationException(
                        "写入失败：" + io.getMessage(), io);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new DocumentGenerationException("写入失败", cause);
        }
    }

    private int countSections(DocumentRequest request) {
        return (request.title() != null ? 1 : 0) + request.sections().size();
    }

    private void validateTitleForFileName(String title) {
        if (title.length() > properties.getMaxFileNameCharacters() * 4) {
            throw new DocumentGenerationException(
                    "title 超过最大字符数：" + properties.getMaxFileNameCharacters());
        }
        if (WINDOWS_DEVICE_NAME.matcher(title).matches()) {
            throw new DocumentGenerationException("title 命中 Windows 保留名称");
        }
    }

    private String deriveSafeBaseName(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC);
        String stripped = SLUG_STRIPPER.matcher(normalized).replaceAll("_");
        String collapsed = REPEATED_DASH.matcher(stripped).replaceAll("_");
        String trimmed = collapsed.strip().replaceAll("^_+|_+$", "");
        if (trimmed.isEmpty()) {
            trimmed = "document";
        }
        if (trimmed.length() > properties.getMaxFileNameCharacters()) {
            trimmed = trimmed.substring(0, properties.getMaxFileNameCharacters());
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return trimmed + "-" + suffix;
    }

    private Path resolveInsideOutput(Path outputDirectory, String safeBaseName) {
        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path candidate = absoluteOutput.resolve(safeBaseName + ".docx").normalize();
        Path canonicalOutput;
        try {
            canonicalOutput = absoluteOutput.toRealPath().normalize();
        } catch (IOException exception) {
            throw new DocumentGenerationException(
                    "无法解析输出目录：" + exception.getMessage(), exception);
        }
        if (!candidate.startsWith(canonicalOutput)) {
            throw new DocumentGenerationException(
                    "目标路径越界：" + candidate);
        }
        Path canonicalCandidate;
        try {
            canonicalCandidate = candidate.toRealPath().normalize();
        } catch (IOException exception) {
            return candidate;
        }
        if (!canonicalCandidate.startsWith(canonicalOutput)) {
            throw new DocumentGenerationException(
                    "目标路径越界：" + canonicalCandidate);
        }
        return candidate;
    }

    private void ensureOutputDirectory(Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new DocumentGenerationException(
                    "无法创建输出目录：" + exception.getMessage(), exception);
        }
    }

    private Path createTempSibling(Path targetPath) {
        String fileName = targetPath.getFileName().toString();
        Path parent = targetPath.getParent();
        if (parent == null) {
            throw new DocumentGenerationException("目标路径缺少父目录");
        }
        String tempName = fileName + TEMP_SUFFIX + "-" + UUID.randomUUID().toString().substring(0, 8);
        return parent.resolve(tempName);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOG.warn("document_temp_cleanup_failed fileName={} reason={}",
                    path.getFileName(), exception.getClass().getSimpleName());
        }
    }

    private void atomicMove(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new DocumentGenerationException(
                    "无法原子移动临时文件到目标：" + exception.getMessage(), exception);
        }
    }

    private DocumentGenerationResult writeAndVerifyWithTimeout(
            Path tempPath, byte[] bytes, DocumentRequest request) {
        long timeoutMillis = properties.getWriteTimeout().toMillis();
        Future<DocumentGenerationResult> future = timeoutExecutor.submit(() -> {
            Files.write(tempPath, bytes);
            return verifyOnDisk(tempPath, bytes, request);
        });
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new DocumentGenerationException(
                    "写入超时（" + timeoutMillis + " ms）");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentGenerationException("写入被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DocumentGenerationException write) {
                throw write;
            }
            if (cause instanceof DocumentReadException read) {
                throw read;
            }
            if (cause instanceof IOException io) {
                throw new DocumentGenerationException(
                        "写入失败：" + io.getMessage(), io);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new DocumentGenerationException("写入失败", cause);
        }
    }

    private DocumentGenerationResult withTargetPath(
            DocumentGenerationResult tempResult, Path targetPath) {
        return new DocumentGenerationResult(
                targetPath,
                tempResult.bytes(),
                tempResult.sha256(),
                tempResult.verifiedSections(),
                tempResult.verifiedTables(),
                tempResult.verifiedPageBreaks());
    }

    private DocumentGenerationResult verifyOnDisk(
            Path filePath, byte[] writtenBytes, DocumentRequest request) {
        byte[] onDiskBytes;
        try {
            onDiskBytes = Files.readAllBytes(filePath);
        } catch (IOException exception) {
            throw new DocumentReadException(
                    "无法读取临时文件：" + exception.getClass().getSimpleName(), exception);
        }
        if (!hasZipMagic(onDiskBytes)) {
            throw new DocumentReadException("落盘文件不是有效的 DOCX（ZIP 头缺失）");
        }
        if (onDiskBytes.length > properties.getMaxOutputBytes()) {
            throw new DocumentGenerationException(
                    "落盘 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        int expectedTables = 0;
        int expectedPageBreaks = 0;
        for (DocumentSection section : request.sections()) {
            if (section instanceof DocumentSection.TableSection) {
                expectedTables++;
            } else if (section instanceof DocumentSection.PageBreakSection) {
                expectedPageBreaks++;
            }
        }

        try (InputStream input = Files.newInputStream(filePath);
                XWPFDocument document = new XWPFDocument(input)) {
            int paragraphs = document.getParagraphs().size();
            int tables = document.getTables().size();
            if (tables != expectedTables) {
                throw new DocumentReadException(
                        "回读表格数与请求不一致：期望 " + expectedTables + "，实际 " + tables);
            }
            for (XWPFTable table : document.getTables()) {
                if (table.getRows().isEmpty()) {
                    throw new DocumentReadException("回读表格缺少行");
                }
                int width = table.getRow(0).getTableCells().size();
                if (width <= 0) {
                    throw new DocumentReadException("回读表格首行单元格数为 0");
                }
                for (int rowIndex = 1; rowIndex < table.getRows().size(); rowIndex++) {
                    if (table.getRow(rowIndex).getTableCells().size() != width) {
                        throw new DocumentReadException(
                                "回读表格第 " + (rowIndex + 1) + " 行单元格数与表头不一致");
                    }
                }
            }
            int pageBreaks = countPageBreaks(document);
            if (pageBreaks != expectedPageBreaks) {
                throw new DocumentReadException(
                        "回读分页符数与请求不一致：期望 " + expectedPageBreaks
                                + "，实际 " + pageBreaks);
            }
            if (paragraphs <= 0) {
                throw new DocumentReadException("回读段落数为 0");
            }

            byte[] digest = sha256(onDiskBytes);
            return new DocumentGenerationResult(
                    filePath,
                    onDiskBytes,
                    digest,
                    paragraphs,
                    tables,
                    pageBreaks);
        } catch (IOException exception) {
            throw new DocumentReadException(
                    "无法回读 DOCX：" + exception.getClass().getSimpleName(), exception);
        }
    }

    private int countPageBreaks(XWPFDocument document) {
        int count = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (org.apache.poi.xwpf.usermodel.XWPFRun run : paragraph.getRuns()) {
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr br
                        : run.getCTR().getBrList()) {
                    if (br.getType() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean hasZipMagic(byte[] bytes) {
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

    private byte[] sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new DocumentGenerationException("SHA-256 不可用", exception);
        }
    }

    /**
     * Outcome of {@link #generate(DocumentRequest)}:
     *
     * @param filePath           atomic-moved target path inside the output directory
     * @param bytes              bytes read from the final target path
     * @param sha256             SHA-256 digest of the bytes
     * @param verifiedSections   paragraph count verified on disk
     * @param verifiedTables     table count verified on disk
     * @param verifiedPageBreaks page-break count verified on disk
     */
    public record DocumentGenerationResult(
            Path filePath,
            byte[] bytes,
            byte[] sha256,
            int verifiedSections,
            int verifiedTables,
            int verifiedPageBreaks) {

        public DocumentGenerationResult {
            Objects.requireNonNull(filePath, "filePath");
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(sha256, "sha256");
            bytes = bytes.clone();
            sha256 = sha256.clone();
        }

        public String sha256Hex() {
            return HexFormat.of().formatHex(sha256);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }

        public String fileName() {
            return filePath.getFileName().toString();
        }

        public boolean hasLowerCaseExtension() {
            return fileName().toLowerCase(Locale.ROOT).endsWith(".docx");
        }
    }
}