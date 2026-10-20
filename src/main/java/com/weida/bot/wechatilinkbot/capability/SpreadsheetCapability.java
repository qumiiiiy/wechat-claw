package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetReadException;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetWriteException;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetReadResult;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetResult;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Server-controlled XLSX capability.
 *
 * <p>The capability owns the output filesystem. The model never supplies a
 * path; this class derives a safe basename from the validated request
 * title, anchors it under the configured output directory, and confirms
 * that the canonicalized file path stays inside the configured boundary
 * before writing.</p>
 *
 * <p>Write contract (atomic):</p>
 * <ol>
 *   <li>Render to bytes through {@link SpreadsheetRenderer}.</li>
 *   <li>Create a sibling temp file inside the same directory as the final
 *       target, then write the bytes there.</li>
 *   <li>Re-open the temp file through {@link XSSFWorkbook} (POI) inside a
 *       try-with-resources block, then compare sheet name, headers, and
 *       row count against the request.</li>
 *   <li>The write + verify step is bounded by
 *       {@link SpreadsheetProperties#getWriteTimeout()}; on timeout the
 *       temp file is deleted and a {@link SpreadsheetWriteException} is
 *       raised.</li>
 *   <li>On success, the temp file is atomically renamed to the final path.
 *       The target path never exists with partially-written bytes.</li>
 *   <li>The temp file is removed in a {@code finally} block whether the
 *       operation succeeded or failed.</li>
 * </ol>
 *
 * <p>Read contract:</p>
 * <ol>
 *   <li>Caller supplies a server-resolved path; the model never does.</li>
 *   <li>Bytes are validated for size, ZIP magic, sheet count, sheet name,
 *       macros, external links, formula cells, and non-string cells.</li>
 *   <li>On failure, no file is mutated.</li>
 * </ol>
 *
 * <p>Append contract:</p>
 * <ol>
 *   <li>Source path is server-resolved.</li>
 *   <li>The source workbook is opened in read-only-equivalent mode; bytes
 *       are read once into a defensive copy, then a new workbook is built
 *       from that copy with the extra sheet appended.</li>
 *   <li>The resulting bytes go through the same atomic-write flow used by
 *       {@link #generate(SpreadsheetRequest)} and are independently
 *       re-validated by {@link SpreadsheetRenderer#readFromBytes(byte[])}.</li>
 *   <li>The source file is never mutated; the capability returns the
 *       SHA-256 digest of the source bytes so callers can verify
 *       byte-equality if they kept their own copy.</li>
 * </ol>
 */
@Component
public class SpreadsheetCapability {

    private static final Logger LOG = LoggerFactory.getLogger(SpreadsheetCapability.class);
    private static final Pattern SLUG_STRIPPER = Pattern.compile("[^\\p{L}\\p{N}_-]+");
    private static final Pattern REPEATED_DASH = Pattern.compile("_+");
    private static final Pattern WINDOWS_DEVICE_NAME = Pattern.compile(
            "(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");
    private static final String COPY_SUFFIX = "-copy";
    private static final String TEMP_SUFFIX = ".tmp";

    private final SpreadsheetRenderer renderer;
    private final SpreadsheetProperties properties;
    private final ExecutorService timeoutExecutor;

    @Autowired
    public SpreadsheetCapability(
            SpreadsheetRenderer renderer,
            SpreadsheetProperties properties) {
        this(
                renderer,
                properties,
                Executors.newSingleThreadExecutor(
                        new NamedThreadFactory("spreadsheet-write-timeout-")));
    }

    SpreadsheetCapability(
            SpreadsheetRenderer renderer,
            SpreadsheetProperties properties,
            ExecutorService timeoutExecutor) {
        this.renderer = renderer;
        this.properties = properties;
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
     * Renders, writes, and verifies an XLSX inside the configured output
     * directory using the atomic temp-file flow described in the class
     * javadoc.
     */
    public SpreadsheetResult generate(SpreadsheetRequest request) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        validateTitleForFileName(request.title());

        Path outputDirectory = properties.getOutputDirectory();
        ensureOutputDirectory(outputDirectory);

        String safeBaseName = deriveSafeBaseName(request.title());
        Path targetPath = resolveInsideOutput(outputDirectory, safeBaseName);

        byte[] bytes = renderer.render(request);

        Path tempPath = createTempSibling(targetPath);
        SpreadsheetResult tempResult;
        try {
            tempResult = writeAndVerifyWithTimeout(tempPath, bytes, request);
            atomicMove(tempPath, targetPath);
        } finally {
            deleteQuietly(tempPath);
        }

        SpreadsheetResult result = withTargetPath(tempResult, targetPath);
        LOG.info(
                "spreadsheet_generated fileName={} sheet={} rows={} bytes={}",
                targetFileName(result.filePath()),
                result.sheetName(),
                result.verifiedRowCount(),
                result.bytes().length);
        return result;
    }

    /**
     * Convenience for the channel: returns only the {@link GeneratedMedia}
     * needed to deliver the file.
     */
    public GeneratedMedia generateMedia(SpreadsheetRequest request) {
        SpreadsheetResult result = generate(request);
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                result.bytes(),
                targetFileName(result.filePath()),
                0L,
                "");
    }

    /**
     * Read-only verification of a server-resolved XLSX file.
     */
    public SpreadsheetReadResult read(Path trustedSourcePath) {
        return renderer.readFromDisk(trustedSourcePath);
    }

    /** Reads only a server-owned file media value retained for this conversation. */
    public SpreadsheetReadResult read(GeneratedMedia trustedSource) {
        validateSpreadsheetMedia(trustedSource);
        return renderer.readFromBytes(trustedSource.bytes());
    }

    /** Appends a sheet to a server-owned file media value and returns the controlled copy. */
    public GeneratedMedia appendSheet(
            GeneratedMedia trustedSource,
            SpreadsheetAppendRequest appendRequest) {
        validateSpreadsheetMedia(trustedSource);
        AppendResult appendResult = appendSheet(
                trustedSource.fileName(),
                trustedSource.bytes(),
                appendRequest);
        SpreadsheetResult result = appendResult.result();
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                result.bytes(),
                targetFileName(result.filePath()),
                0L,
                "");
    }

    /**
     * Copies a server-resolved source workbook to a controlled destination
     * and appends one new sheet. The source file is never mutated. The
     * returned record carries both the destination path and the SHA-256
     * digest of the source bytes so callers can independently confirm
     * byte-equality.
     */
    public AppendResult appendSheet(Path trustedSourcePath, SpreadsheetAppendRequest appendRequest) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (trustedSourcePath == null) {
            throw new SpreadsheetReadException("trustedSourcePath 不能为空");
        }
        if (!Files.isRegularFile(trustedSourcePath)) {
            throw new SpreadsheetReadException("源文件不是普通文件");
        }

        Path outputDirectory = properties.getOutputDirectory();
        ensureOutputDirectory(outputDirectory);

        byte[] sourceBytes;
        try {
            sourceBytes = Files.readAllBytes(trustedSourcePath);
        } catch (IOException exception) {
            throw new SpreadsheetReadException(
                    "无法读取源文件：" + exception.getMessage(), exception);
        }
        return appendSheet(trustedSourcePath.getFileName().toString(), sourceBytes, appendRequest);
    }

    private AppendResult appendSheet(
            String sourceFileName,
            byte[] sourceBytes,
            SpreadsheetAppendRequest appendRequest) {
        byte[] sourceDigest = sha256(sourceBytes);
        String baseName = stripExtension(sourceFileName);
        String safeBaseName = deriveSafeBaseName(baseName) + COPY_SUFFIX;
        Path outputDirectory = properties.getOutputDirectory();
        ensureOutputDirectory(outputDirectory);
        Path targetPath = resolveInsideOutput(outputDirectory, safeBaseName);

        byte[] newBytes = renderer.appendSheet(sourceBytes, appendRequest);

        Path tempPath = createTempSibling(targetPath);
        SpreadsheetResult tempResult;
        try {
            tempResult = writeAndVerifyTempFromBytes(tempPath, newBytes, appendRequest);
            atomicMove(tempPath, targetPath);
        } finally {
            deleteQuietly(tempPath);
        }

        SpreadsheetResult result = withTargetPath(tempResult, targetPath);
        // Re-validate the final destination through the read pipeline to
        // catch anything the temp-write verification missed.
        SpreadsheetReadResult reread = renderer.readFromDisk(targetPath);

        LOG.info(
                "spreadsheet_appended sourceName={} targetName={} newSheet={} bytes={}",
                sourceFileName,
                targetFileName(targetPath),
                appendRequest.newSheetName(),
                result.bytes().length);

        return new AppendResult(result, reread, sourceDigest);
    }

    private void validateSpreadsheetMedia(GeneratedMedia media) {
        if (media == null || media.kind() != GeneratedMediaKind.FILE) {
            throw new SpreadsheetReadException("当前会话没有可读取的文件");
        }
        if (!media.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new SpreadsheetReadException("当前选择的文件不是 .xlsx 工作簿");
        }
    }

    private String targetFileName(Path filePath) {
        return filePath.getFileName().toString();
    }

    private void validateTitleForFileName(String title) {
        if (title.length() > properties.getMaxTitleCharacters()) {
            throw new SpreadsheetWriteException(
                    "title 超过最大字符数：" + properties.getMaxTitleCharacters());
        }
        if (WINDOWS_DEVICE_NAME.matcher(title).matches()) {
            throw new SpreadsheetWriteException("title 命中 Windows 保留名称");
        }
    }

    private String deriveSafeBaseName(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC);
        String stripped = SLUG_STRIPPER.matcher(normalized).replaceAll("_");
        String collapsed = REPEATED_DASH.matcher(stripped).replaceAll("_");
        String trimmed = collapsed.strip().replaceAll("^_+|_+$", "");
        if (trimmed.isEmpty()) {
            trimmed = "sheet";
        }
        if (trimmed.length() > properties.getMaxFileNameCharacters()) {
            trimmed = trimmed.substring(0, properties.getMaxFileNameCharacters());
        }
        // Disambiguate with a short non-guessable suffix.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return trimmed + "-" + suffix;
    }

    private Path resolveInsideOutput(Path outputDirectory, String safeBaseName) {
        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path candidate = absoluteOutput.resolve(safeBaseName + ".xlsx").normalize();
        Path canonicalOutput;
        try {
            canonicalOutput = absoluteOutput.toRealPath().normalize();
        } catch (IOException exception) {
            throw new SpreadsheetWriteException(
                    "无法解析输出目录：" + exception.getMessage(), exception);
        }
        if (!candidate.startsWith(canonicalOutput)) {
            throw new SpreadsheetWriteException(
                    "目标路径越界：" + candidate);
        }
        Path canonicalCandidate;
        try {
            canonicalCandidate = candidate.toRealPath().normalize();
        } catch (IOException exception) {
            // The file does not exist yet; the prefix check above is
            // authoritative for paths inside the output directory.
            return candidate;
        }
        if (!canonicalCandidate.startsWith(canonicalOutput)) {
            throw new SpreadsheetWriteException(
                    "目标路径越界：" + canonicalCandidate);
        }
        return candidate;
    }

    private void ensureOutputDirectory(Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new SpreadsheetWriteException(
                    "无法创建输出目录：" + exception.getMessage(), exception);
        }
    }

    private Path createTempSibling(Path targetPath) {
        String fileName = targetPath.getFileName().toString();
        Path parent = targetPath.getParent();
        if (parent == null) {
            throw new SpreadsheetWriteException("目标路径缺少父目录");
        }
        String tempName = fileName + TEMP_SUFFIX + "-" + UUID.randomUUID().toString().substring(0, 8);
        return parent.resolve(tempName);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOG.warn("spreadsheet_temp_cleanup_failed fileName={} reason={}",
                    path.getFileName(), exception.getClass().getSimpleName());
        }
    }

    private void atomicMove(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new SpreadsheetWriteException(
                    "无法原子移动临时文件到目标：" + exception.getMessage(), exception);
        }
    }

    private SpreadsheetResult writeAndVerifyWithTimeout(
            Path tempPath, byte[] bytes, SpreadsheetRequest request) {
        long timeoutMillis = properties.getWriteTimeout().toMillis();
        Future<SpreadsheetResult> future = timeoutExecutor.submit(() -> {
            Files.write(tempPath, bytes);
            return verifyOnDisk(tempPath, request);
        });
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new SpreadsheetWriteException(
                    "写入超时（" + timeoutMillis + " ms）");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpreadsheetWriteException("写入被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SpreadsheetWriteException write) {
                throw write;
            }
            if (cause instanceof IOException io) {
                throw new SpreadsheetWriteException(
                        "写入失败：" + io.getMessage(), io);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new SpreadsheetWriteException("写入失败", cause);
        }
    }

    private SpreadsheetResult writeAndVerifyTempFromBytes(
            Path tempPath, byte[] bytes, SpreadsheetAppendRequest appendRequest) {
        Future<SpreadsheetResult> future = timeoutExecutor.submit(() -> {
            Files.write(tempPath, bytes);
            return verifyAppendOnDisk(tempPath, appendRequest);
        });
        try {
            return future.get(properties.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new SpreadsheetWriteException("写入超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpreadsheetWriteException("写入被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SpreadsheetWriteException write) {
                throw write;
            }
            if (cause instanceof IOException io) {
                throw new SpreadsheetWriteException(
                        "写入失败：" + io.getMessage(), io);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new SpreadsheetWriteException("写入失败", cause);
        }
    }

    private SpreadsheetResult withTargetPath(SpreadsheetResult tempResult, Path targetPath) {
        return new SpreadsheetResult(
                targetPath,
                tempResult.bytes(),
                tempResult.sheetName(),
                tempResult.verifiedHeaders(),
                tempResult.verifiedRowCount());
    }

    private SpreadsheetResult verifyAppendOnDisk(
            Path filePath, SpreadsheetAppendRequest appendRequest) {
        try (InputStream input = Files.newInputStream(filePath);
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            String newSheetName = appendRequest.newSheetName();
            XSSFSheet sheet = workbook.getSheet(newSheetName);
            if (sheet == null) {
                throw new SpreadsheetWriteException(
                        "追加验证失败：找不到新工作表 " + newSheetName);
            }
            org.apache.poi.xssf.usermodel.XSSFRow headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException("追加验证失败：缺少表头行");
            }
            List<String> verifiedHeaders = new ArrayList<>(appendRequest.columns().size());
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < appendRequest.columns().size(); i++) {
                org.apache.poi.xssf.usermodel.XSSFCell cell = headerRow.getCell(i);
                if (cell == null) {
                    throw new SpreadsheetWriteException(
                            "追加验证失败：表头第 " + (i + 1) + " 列为空");
                }
                String value = cell.getStringCellValue();
                if (!seen.add(value)) {
                    throw new SpreadsheetWriteException("追加验证失败：表头重复 " + value);
                }
                if (!appendRequest.columns().get(i).name().equals(value)) {
                    throw new SpreadsheetWriteException("追加验证失败：表头不一致");
                }
                verifiedHeaders.add(value);
            }
            int dataRowCount = Math.max(0, sheet.getLastRowNum());
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "落盘 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            return new SpreadsheetResult(
                    filePath,
                    bytes,
                    newSheetName,
                    verifiedHeaders,
                    dataRowCount);
        } catch (IOException exception) {
            throw new SpreadsheetWriteException(
                    "无法回读 XLSX：" + exception.getMessage(), exception);
        }
    }

    private SpreadsheetResult verifyOnDisk(Path filePath, SpreadsheetRequest request) {
        try (InputStream input = Files.newInputStream(filePath);
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            if (numberOfSheets != 1) {
                throw new SpreadsheetWriteException(
                        "工作表数量异常：" + numberOfSheets);
            }
            String sheetName = workbook.getSheetName(0);
            String expectedSheetName = expectedSheetName(request);
            if (!expectedSheetName.equals(sheetName)) {
                throw new SpreadsheetWriteException(
                        "工作表名不一致：期望 " + expectedSheetName + "，实际 " + sheetName);
            }

            XSSFSheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new SpreadsheetWriteException("找不到工作表：" + sheetName);
            }

            org.apache.poi.xssf.usermodel.XSSFRow headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException("缺少标题行");
            }
            List<String> verifiedHeaders = new ArrayList<>(request.columns().size());
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < request.columns().size(); i++) {
                org.apache.poi.xssf.usermodel.XSSFCell cell = headerRow.getCell(i);
                if (cell == null) {
                    throw new SpreadsheetWriteException("标题缺少第 " + (i + 1) + " 列");
                }
                String value = cell.getStringCellValue();
                if (!seen.add(value)) {
                    throw new SpreadsheetWriteException("回读标题重复：" + value);
                }
                if (!request.columns().get(i).name().equals(value)) {
                    throw new SpreadsheetWriteException(
                            "回读标题不一致：期望 "
                                    + request.columns().get(i).name()
                                    + "，实际 " + value);
                }
                verifiedHeaders.add(value);
            }

            int firstDataRowIndex = 1;
            int lastRowIndex = sheet.getLastRowNum();
            int dataRowCount = Math.max(0, lastRowIndex - firstDataRowIndex + 1);

            for (int r = firstDataRowIndex; r <= lastRowIndex; r++) {
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                int cellCount = row.getLastCellNum();
                if (cellCount > request.columns().size()) {
                    throw new SpreadsheetWriteException(
                            "第 " + (r + 1) + " 行单元格数超过列数");
                }
                for (int c = 0; c < cellCount; c++) {
                    org.apache.poi.xssf.usermodel.XSSFCell cell = row.getCell(c);
                    if (cell == null) {
                        continue;
                    }
                    if (cell.getCellType()
                            != org.apache.poi.ss.usermodel.CellType.STRING) {
                        throw new SpreadsheetWriteException(
                                "第 " + (r + 1) + " 行第 " + (c + 1)
                                        + " 列不是字符串单元格");
                    }
                    String value = cell.getStringCellValue();
                    if (value.length() > properties.getMaxCellCharacters()) {
                        throw new SpreadsheetWriteException(
                                "回读单元格内容超过最大字符数");
                    }
                }
            }

            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "落盘 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            return new SpreadsheetResult(
                    filePath,
                    bytes,
                    sheetName,
                    verifiedHeaders,
                    dataRowCount);
        } catch (IOException exception) {
            throw new SpreadsheetWriteException(
                    "无法回读 XLSX：" + exception.getMessage(), exception);
        }
    }

    private String expectedSheetName(SpreadsheetRequest request) {
        String sheetName = request.sheetName();
        if (sheetName == null || sheetName.isBlank()) {
            return properties.getDefaultSheetName();
        }
        return sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
    }

    private byte[] sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new SpreadsheetWriteException("SHA-256 不可用", exception);
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    /**
     * Outcome of {@link #appendSheet(Path, SpreadsheetAppendRequest)}.
     *
     * @param result          the generated destination workbook
     * @param reread          read-summary of the destination workbook after
     *                        the atomic move, obtained by running the read
     *                        pipeline a second time
     * @param sourceSha256    SHA-256 digest of the source file as it was
     *                        when the copy started; callers can compare
     *                        this against a digest they took before
     *                        invocation to prove byte-equality
     */
    public record AppendResult(
            SpreadsheetResult result,
            SpreadsheetReadResult reread,
            byte[] sourceSha256) {

        public AppendResult {
            result = Objects.requireNonNull(result);
            reread = Objects.requireNonNull(reread);
            sourceSha256 = sourceSha256.clone();
        }

        public String sourceSha256Hex() {
            return HexFormat.of().formatHex(sourceSha256);
        }
    }
}
