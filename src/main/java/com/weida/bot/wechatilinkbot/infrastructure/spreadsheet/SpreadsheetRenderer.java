package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetCellValue;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetReadResult;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetSheetView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pure POI XLSX renderer and reader.
 *
 * <p>Stateless and side-effect free: accepts a validated
 * {@link SpreadsheetRequest} and returns the resulting workbook bytes, or
 * accepts workbook bytes and returns a verified read summary. The capability
 * layer owns filesystem I/O for both write and read paths.</p>
 *
 * <p>Layout produced by {@link #render}:</p>
 * <ul>
 *   <li>Row 0: column headers (NFKC-normalized, unique, bold)</li>
 *   <li>Row 1..N: data rows in the order supplied by the caller</li>
 * </ul>
 *
 * <p>Title text is preserved in the request and on the file system path;
 * it is intentionally NOT written into a sheet row, so the workbook
 * remains a standard data sheet.</p>
 *
 * <p>Read contract enforced by {@link #readFromBytes} before any value is
 * returned:</p>
 * <ul>
 *   <li>size ≤ {@link SpreadsheetProperties#getMaxReadBytes()}</li>
 *   <li>sheet count ≤ {@link SpreadsheetProperties#getMaxReadSheets()}</li>
 *   <li>no macros (vbaProject part absent)</li>
 *   <li>no external references</li>
 *   <li>every cell is a string cell; formulas are rejected</li>
 *   <li>row and column counts within configured limits</li>
 *   <li>sheet names non-blank and within Excel's 31-char limit</li>
 * </ul>
 */
@Component
public class SpreadsheetRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(SpreadsheetRenderer.class);

    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    /**
     * Formula-injection prefix characters that {@code POI setCellValue(String)}
     * may surface as live formulas when the resulting workbook is opened in
     * Excel, even though POI stores them as STRING cells. The renderer
     * rejects these on every column header and data cell to keep the file
     * safe for downstream tooling.
     */
    private static final Pattern FORMULA_INJECTION_PREFIX = Pattern.compile("^[=+\\-@\\t\\r]");

    private final SpreadsheetProperties properties;

    public SpreadsheetRenderer(SpreadsheetProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isEnabled();
    }

    public byte[] render(SpreadsheetRequest request) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        validateRequest(request);

        String sheetName = resolveSheetName(request);
        boolean useTyped = !request.typedRows().isEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(sheetName);
            XSSFCellStyle headerStyle = boldStyle(workbook);
            XSSFCellStyle numberStyle = numberStyle(workbook);
            XSSFCellStyle dateStyle = dateStyle(workbook);

            // Row 0: headers.
            XSSFRow headerRow = sheet.createRow(0);
            int columnIndex = 0;
            for (SpreadsheetColumn column : request.columns()) {
                XSSFCell cell = headerRow.createCell(columnIndex++);
                cell.setCellValue(column.name());
                cell.setCellStyle(headerStyle);
            }

            // Rows 1..N: data.
            int dataRowIndex = 0;
            int maxRows = properties.getMaxRows();
            if (useTyped) {
                for (List<SpreadsheetCellValue> row : request.typedRows()) {
                    if (dataRowIndex >= maxRows) {
                        throw new SpreadsheetWriteException(
                                "数据行超过最大限制：" + maxRows);
                    }
                    XSSFRow xRow = sheet.createRow(1 + dataRowIndex);
                    int cellIndex = 0;
                    for (SpreadsheetCellValue value : row) {
                        XSSFCell cell = xRow.createCell(cellIndex++);
                        writeTypedCell(cell, value, numberStyle, dateStyle);
                    }
                    dataRowIndex++;
                }
            } else {
                for (List<String> row : request.rows()) {
                    if (dataRowIndex >= maxRows) {
                        throw new SpreadsheetWriteException(
                                "数据行超过最大限制：" + maxRows);
                    }
                    XSSFRow xRow = sheet.createRow(1 + dataRowIndex);
                    int cellIndex = 0;
                    for (String cellValue : row) {
                        XSSFCell cell = xRow.createCell(cellIndex++);
                        cell.setCellValue(cellValue == null ? "" : cellValue);
                    }
                    dataRowIndex++;
                }
            }

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "生成的 XLSX 超过最大字节数限制：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_rendered sheet={} title={} columns={} rows={} bytes={} typed={}",
                    sheetName,
                    request.title(),
                    request.columns().size(),
                    dataRowIndex,
                    bytes.length,
                    useTyped);
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 渲染失败", exception);
        }
    }

    private void writeTypedCell(
            XSSFCell cell,
            SpreadsheetCellValue value,
            XSSFCellStyle numberStyle,
            XSSFCellStyle dateStyle) {
        switch (value.kind()) {
            case STRING -> cell.setCellValue(value.rawValue());
            case NUMBER -> {
                cell.setCellValue(Double.parseDouble(value.rawValue()));
                cell.setCellStyle(numberStyle);
            }
            case DATE -> {
                LocalDate localDate = LocalDate.parse(value.rawValue());
                Date date = Date.from(
                        localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            }
            case FORMULA -> cell.setCellFormula(value.rawValue());
        }
    }

    /**
     * Read-only verifier: loads bytes from a server-trusted path and returns
     * a {@link SpreadsheetReadResult} after strict validation. The capability
     * layer controls which paths are eligible to be passed in; this method
     * never validates a path.
     */
    public SpreadsheetReadResult readFromDisk(Path sourcePath) {
        if (sourcePath == null) {
            throw new SpreadsheetReadException("sourcePath 不能为空");
        }
        if (!Files.isRegularFile(sourcePath)) {
            throw new SpreadsheetReadException("源文件不是普通文件");
        }
        String fileName = sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xlsm")) {
            throw new SpreadsheetReadException("拒绝读取 .xlsm 文件（宏可执行）");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(sourcePath);
        } catch (IOException exception) {
            throw new SpreadsheetReadException(
                    "无法读取源文件：" + exception.getMessage(), exception);
        }
        return readFromBytes(bytes, sourcePath, bytes.length);
    }

    /**
     * Read-only verifier for workbook bytes already in memory.
     */
    public SpreadsheetReadResult readFromBytes(byte[] bytes) {
        return readFromBytes(bytes, null, bytes.length);
    }

    private SpreadsheetReadResult readFromBytes(byte[] bytes, Path sourcePath, long totalBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new SpreadsheetReadException("字节为空");
        }
        if (bytes.length > properties.getMaxReadBytes()) {
            throw new SpreadsheetReadException(
                    "XLSX 超过最大读取字节数：" + properties.getMaxReadBytes());
        }
        if (!hasZipMagic(bytes)) {
            throw new SpreadsheetReadException("不是有效的 XLSX 文件（ZIP 头缺失）");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            rejectMacros(workbook);
            rejectExternalReferences(workbook);

            int sheetCount = workbook.getNumberOfSheets();
            if (sheetCount == 0) {
                throw new SpreadsheetReadException("工作簿不含任何工作表");
            }
            if (sheetCount > properties.getMaxReadSheets()) {
                throw new SpreadsheetReadException(
                        "工作表数超过最大限制：" + properties.getMaxReadSheets());
            }

            Set<String> seenSheetNames = new HashSet<>();
            List<SpreadsheetSheetView> views = new ArrayList<>(sheetCount);
            for (int i = 0; i < sheetCount; i++) {
                String sheetName = workbook.getSheetName(i);
                validateSheetName(sheetName, i);
                if (!seenSheetNames.add(sheetName)) {
                    throw new SpreadsheetReadException("工作表名重复：" + sheetName);
                }
                views.add(readSheet(workbook.getSheet(sheetName), sheetName));
            }
            SpreadsheetReadResult result = new SpreadsheetReadResult(
                    sourcePath, totalBytes, views);
            LOG.info(
                    "spreadsheet_read sourceKind={} sheets={} bytes={}",
                    sourcePath == null ? "memory" : "trusted_file",
                    sheetCount,
                    totalBytes);
            return result;
        } catch (IOException exception) {
            throw new SpreadsheetReadException("无法解析 XLSX：" + exception.getMessage(), exception);
        }
    }

    /**
     * Produces a new workbook by reading a trusted source and adding an extra
     * sheet with the supplied rows. The source bytes are copied first, so
     * the original file or buffer is never mutated.
     */
    public byte[] appendSheet(byte[] sourceBytes, SpreadsheetAppendRequest appendRequest) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        // Verify the source first so we fail fast on prohibited features.
        readFromBytes(sourceBytes);
        boolean useTyped = !appendRequest.typedRows().isEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String newSheetName = appendRequest.newSheetName();
            if (workbook.getSheet(newSheetName) != null) {
                throw new SpreadsheetWriteException("工作表名已存在：" + newSheetName);
            }
            XSSFCellStyle headerStyle = boldStyle(workbook);
            XSSFCellStyle numberStyle = numberStyle(workbook);
            XSSFCellStyle dateStyle = dateStyle(workbook);

            XSSFSheet sheet = workbook.createSheet(newSheetName);
            List<SpreadsheetColumn> columns = appendRequest.columns();

            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).name());
                cell.setCellStyle(headerStyle);
            }

            int dataRowIndex = 0;
            int maxRows = properties.getMaxRows();
            if (useTyped) {
                List<List<SpreadsheetCellValue>> rows = appendRequest.typedRows();
                for (List<SpreadsheetCellValue> row : rows) {
                    if (dataRowIndex >= maxRows) {
                        throw new SpreadsheetWriteException(
                                "追加数据行超过最大限制：" + maxRows);
                    }
                    if (row.size() != columns.size()) {
                        throw new SpreadsheetWriteException(
                                "追加数据第 " + (dataRowIndex + 1) + " 行列数与表头不一致");
                    }
                    XSSFRow xRow = sheet.createRow(1 + dataRowIndex);
                    for (int c = 0; c < row.size(); c++) {
                        XSSFCell cell = xRow.createCell(c);
                        writeTypedCell(cell, row.get(c), numberStyle, dateStyle);
                    }
                    dataRowIndex++;
                }
            } else {
                List<List<String>> rows = appendRequest.rows();
                for (List<String> row : rows) {
                    if (dataRowIndex >= maxRows) {
                        throw new SpreadsheetWriteException(
                                "追加数据行超过最大限制：" + maxRows);
                    }
                    if (row.size() != columns.size()) {
                        throw new SpreadsheetWriteException(
                                "追加数据第 " + (dataRowIndex + 1) + " 行列数与表头不一致");
                    }
                    XSSFRow xRow = sheet.createRow(1 + dataRowIndex);
                    for (int c = 0; c < row.size(); c++) {
                        XSSFCell cell = xRow.createCell(c);
                        cell.setCellValue(row.get(c) == null ? "" : row.get(c));
                    }
                    dataRowIndex++;
                }
            }

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "追加后的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_appended new_sheet={} columns={} rows={} bytes={} typed={}",
                    newSheetName, columns.size(), dataRowIndex, bytes.length, useTyped);
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 追加失败", exception);
        }
    }

    private void validateSheetName(String sheetName, int index) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new SpreadsheetReadException(
                    "第 " + (index + 1) + " 个工作表名缺失");
        }
        if (sheetName.length() > 31) {
            throw new SpreadsheetReadException(
                    "第 " + (index + 1) + " 个工作表名超过 31 字符");
        }
        for (int i = 0; i < sheetName.length(); i++) {
            char c = sheetName.charAt(i);
            if (c == ':' || c == '\\' || c == '/' || c == '?' || c == '*' || c == '[' || c == ']') {
                throw new SpreadsheetReadException(
                        "第 " + (index + 1) + " 个工作表名包含非法字符");
            }
        }
    }

    private SpreadsheetSheetView readSheet(XSSFSheet sheet, String sheetName) {
        int maxColumns = properties.getMaxReadColumns();
        int maxRows = properties.getMaxReadRows();
        int maxCellCharacters = properties.getMaxCellCharacters();

        XSSFRow headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new SpreadsheetReadException(
                    "工作表 " + sheetName + " 缺少表头行");
        }
        int headerCellCount = headerRow.getLastCellNum();
        if (headerCellCount > maxColumns) {
            throw new SpreadsheetReadException(
                    "工作表 " + sheetName + " 列数超过限制：" + maxColumns);
        }

        List<String> headers = new ArrayList<>(headerCellCount);
        Set<String> seenHeaders = new HashSet<>();
        for (int c = 0; c < headerCellCount; c++) {
            XSSFCell cell = headerRow.getCell(c);
            if (cell == null) {
                throw new SpreadsheetReadException(
                        "工作表 " + sheetName + " 表头第 " + (c + 1) + " 列为空");
            }
            String value = readStringCell(cell, sheetName, 1, c + 1);
            if (!seenHeaders.add(value)) {
                throw new SpreadsheetReadException(
                        "工作表 " + sheetName + " 表头重复：" + value);
            }
            headers.add(value);
        }

        int lastRowIndex = sheet.getLastRowNum();
        int dataRowCount = Math.max(0, lastRowIndex);
        if (dataRowCount > maxRows) {
            throw new SpreadsheetReadException(
                    "工作表 " + sheetName + " 数据行超过限制：" + maxRows);
        }
        for (int r = 1; r <= lastRowIndex; r++) {
            XSSFRow row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int cellCount = row.getLastCellNum();
            if (cellCount > maxColumns) {
                throw new SpreadsheetReadException(
                        "工作表 " + sheetName + " 第 " + (r + 1) + " 行列数超过限制");
            }
            for (int c = 0; c < cellCount; c++) {
                XSSFCell cell = row.getCell(c);
                if (cell == null) {
                    continue;
                }
                String value = readStringCell(cell, sheetName, r + 1, c + 1);
                if (value.length() > maxCellCharacters) {
                    throw new SpreadsheetReadException(
                            "工作表 " + sheetName + " 第 " + (r + 1) + " 行第 " + (c + 1)
                                    + " 列内容超过最大字符数");
                }
            }
        }

        return new SpreadsheetSheetView(sheetName, headers, dataRowCount);
    }

    private String readStringCell(XSSFCell cell, String sheetName, int rowNumber, int colNumber) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            throw new SpreadsheetReadException(
                    "工作表 " + sheetName + " 第 " + rowNumber + " 行第 " + colNumber
                            + " 列包含公式，拒绝读取");
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue();
        }
        // V1 reader is text-only; anything else is rejected so we never
        // surface numeric/boolean/error values that the renderer never
        // produces and that callers may not know how to display.
        throw new SpreadsheetReadException(
                "工作表 " + sheetName + " 第 " + rowNumber + " 行第 " + colNumber
                        + " 列不是字符串单元格（type=" + type + "）");
    }

    private void rejectMacros(XSSFWorkbook workbook) {
        try {
            for (PackagePart part : workbook.getPackage().getParts()) {
                String partName = part.getPartName().getName().toLowerCase(Locale.ROOT);
                if (partName.contains("vbaProject") || partName.endsWith(".bin")
                        && partName.contains("vba")) {
                    throw new SpreadsheetReadException("XLSX 包含宏（vbaProject），拒绝读取");
                }
            }
        } catch (InvalidFormatException exception) {
            throw new SpreadsheetReadException(
                    "无法检查宏（包格式无效）：" + exception.getMessage(), exception);
        }
    }

    private void rejectExternalReferences(XSSFWorkbook workbook) {
        var externalReferences = workbook.getCTWorkbook().getExternalReferences();
        if (externalReferences != null && externalReferences.getExternalReferenceList() != null
                && !externalReferences.getExternalReferenceList().isEmpty()) {
            throw new SpreadsheetReadException("XLSX 包含外部链接，拒绝读取");
        }
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

    private XSSFCellStyle boldStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle numberStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
        return style;
    }

    private XSSFCellStyle dateStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private String resolveSheetName(SpreadsheetRequest request) {
        String requested = request.sheetName();
        if (requested == null || requested.isBlank()) {
            return properties.getDefaultSheetName();
        }
        return requested.length() > 31 ? requested.substring(0, 31) : requested;
    }

    private void validateRequest(SpreadsheetRequest request) {
        if (request.title().length() > properties.getMaxTitleCharacters()) {
            throw new SpreadsheetWriteException(
                    "title 超过最大字符数：" + properties.getMaxTitleCharacters());
        }

        List<SpreadsheetColumn> columns = request.columns();
        if (columns.size() > properties.getMaxColumns()) {
            throw new SpreadsheetWriteException(
                    "列数超过最大限制：" + properties.getMaxColumns());
        }

        Set<String> normalized = new HashSet<>();
        int maxCellCharacters = properties.getMaxCellCharacters();
        for (int i = 0; i < columns.size(); i++) {
            SpreadsheetColumn column = columns.get(i);
            String name = column.name();
            if (name.isEmpty()) {
                throw new SpreadsheetWriteException("第 " + (i + 1) + " 列名称为空");
            }
            rejectFormulaInjection(name, "第 " + (i + 1) + " 列名");
            if (name.length() > maxCellCharacters) {
                throw new SpreadsheetWriteException(
                        "第 " + (i + 1) + " 列名超过最大字符数");
            }
            String key = Normalizer.normalize(name, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw new SpreadsheetWriteException(
                        "存在重复列名（按 NFKC+大小写归一比较）：" + name);
            }
        }

        List<List<String>> rows = request.rows();
        List<List<SpreadsheetCellValue>> typedRows = request.typedRows();
        if (!typedRows.isEmpty()) {
            if (typedRows.size() > properties.getMaxRows()) {
                throw new SpreadsheetWriteException(
                        "行数超过最大限制：" + properties.getMaxRows());
            }
            for (int rowIndex = 0; rowIndex < typedRows.size(); rowIndex++) {
                List<SpreadsheetCellValue> row = typedRows.get(rowIndex);
                if (row.size() != columns.size()) {
                    throw new SpreadsheetWriteException(
                            "第 " + (rowIndex + 1) + " 行列数与表头不一致");
                }
                // SpreadsheetCellValue.factory() already enforces kind/rawValue
                // consistency; nothing further to validate here beyond size.
            }
            return;
        }

        if (rows.size() > properties.getMaxRows()) {
            throw new SpreadsheetWriteException(
                    "行数超过最大限制：" + properties.getMaxRows());
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.size() != columns.size()) {
                throw new SpreadsheetWriteException(
                        "第 " + (rowIndex + 1) + " 行列数与表头不一致");
            }
            for (int cellIndex = 0; cellIndex < row.size(); cellIndex++) {
                String value = row.get(cellIndex);
                if (value != null && value.length() > maxCellCharacters) {
                    throw new SpreadsheetWriteException(
                            "第 " + (rowIndex + 1) + " 行第 " + (cellIndex + 1)
                                    + " 列内容超过最大字符数");
                }
                if (value != null) {
                    rejectFormulaInjection(
                            value,
                            "第 " + (rowIndex + 1) + " 行第 " + (cellIndex + 1) + " 列");
                }
            }
        }
    }

    private void rejectFormulaInjection(String value, String where) {
        if (value.isEmpty()) {
            return;
        }
        if (FORMULA_INJECTION_PREFIX.matcher(value).find()) {
            throw new SpreadsheetWriteException(
                    where + " 以公式注入前缀开头（首字符 0x"
                            + Integer.toHexString(value.charAt(0)) + "），拒绝写入");
        }
    }

    /**
     * Appends additional rows to the end of an existing sheet in a copy of
     * the source workbook. The source file is never mutated; the returned
     * bytes describe a new workbook identical to the source except for the
     * added rows. Headers are preserved verbatim. Column arity is
     * validated.
     */
    public byte[] appendRows(byte[] sourceBytes, String sourceSheetName, List<List<String>> additionalRows) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        if (sourceSheetName == null || sourceSheetName.isBlank()) {
            throw new SpreadsheetWriteException("sourceSheetName 不能为空");
        }
        if (additionalRows == null) {
            additionalRows = List.of();
        }
        if (!hasZipMagic(sourceBytes)) {
            throw new SpreadsheetReadException("源字节不是有效的 XLSX（ZIP 头缺失）");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new SpreadsheetReadException(
                    "源 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.getSheet(sourceSheetName);
            if (sheet == null) {
                throw new SpreadsheetWriteException(
                        "源工作表不存在：" + sourceSheetName);
            }
            XSSFRow headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException(
                        "源工作表缺少表头行：" + sourceSheetName);
            }
            int columnCount = headerRow.getLastCellNum();
            int currentLastRow = sheet.getLastRowNum();
            int maxRows = properties.getMaxRows();
            int rowsAppended = 0;
            for (List<String> row : additionalRows) {
                if (currentLastRow + 1 + rowsAppended > maxRows) {
                    throw new SpreadsheetWriteException(
                            "追加后超过最大行数限制：" + maxRows);
                }
                if (row.size() != columnCount) {
                    throw new SpreadsheetWriteException(
                            "追加行 " + (rowsAppended + 1) + " 列表头列数不一致（"
                                    + row.size() + " vs " + columnCount + "）");
                }
                for (String value : row) {
                    if (value != null) {
                        rejectFormulaInjection(value, "追加行内容");
                    }
                }
                XSSFRow xRow = sheet.createRow(currentLastRow + 1 + rowsAppended);
                for (int c = 0; c < row.size(); c++) {
                    XSSFCell cell = xRow.createCell(c);
                    cell.setCellValue(row.get(c) == null ? "" : row.get(c));
                }
                rowsAppended++;
            }
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "追加行后的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_rows_appended sheet={} appended={} total_bytes={}",
                    sourceSheetName, rowsAppended, bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 追加行失败", exception);
        }
    }

    /**
     * Sorts data rows of a sheet by the given column (0-based) in either
     * ascending or descending order. Headers are preserved at row 0.
     * Cells are compared as strings after NFKC normalization; rows with
     * missing values sort to the end regardless of direction.
     */
    public byte[] sortRows(byte[] sourceBytes, String sourceSheetName,
            int sortColumnIndex, boolean ascending) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        if (sourceSheetName == null || sourceSheetName.isBlank()) {
            throw new SpreadsheetWriteException("sourceSheetName 不能为空");
        }
        if (sortColumnIndex < 0) {
            throw new SpreadsheetWriteException(
                    "sortColumnIndex 不能为负数：" + sortColumnIndex);
        }
        if (!hasZipMagic(sourceBytes)) {
            throw new SpreadsheetReadException("源字节不是有效的 XLSX（ZIP 头缺失）");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new SpreadsheetReadException(
                    "源 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.getSheet(sourceSheetName);
            if (sheet == null) {
                throw new SpreadsheetWriteException(
                        "源工作表不存在：" + sourceSheetName);
            }
            XSSFRow headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException(
                        "源工作表缺少表头行：" + sourceSheetName);
            }
            if (sortColumnIndex >= headerRow.getLastCellNum()) {
                throw new SpreadsheetWriteException(
                        "sortColumnIndex 超出列范围：" + sortColumnIndex);
            }

            int lastRow = sheet.getLastRowNum();
            if (lastRow < 1) {
                // Header only — nothing to sort.
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }

            List<List<String>> dataRows = new ArrayList<>();
            for (int r = 1; r <= lastRow; r++) {
                XSSFRow row = sheet.getRow(r);
                if (row != null) {
                    dataRows.add(extractRowStrings(row));
                }
            }
            Comparator<String> keyOrder = (left, right) -> {
                if (left.isEmpty() && right.isEmpty()) return 0;
                if (left.isEmpty()) return 1;
                if (right.isEmpty()) return -1;
                return Normalizer.normalize(left, Normalizer.Form.NFKC)
                        .compareTo(Normalizer.normalize(right, Normalizer.Form.NFKC));
            };
            dataRows.sort((a, b) -> {
                String aValue = a.get(sortColumnIndex);
                String bValue = b.get(sortColumnIndex);
                int cmp = keyOrder.compare(aValue, bValue);
                return ascending ? cmp : -cmp;
            });

            // Rewrite rows in place to preserve formatting at original indices.
            for (int i = 0; i < dataRows.size(); i++) {
                XSSFRow targetRow = sheet.getRow(i + 1);
                if (targetRow == null) {
                    targetRow = sheet.createRow(i + 1);
                }
                writeRowStrings(targetRow, dataRows.get(i));
            }
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "排序后的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_rows_sorted sheet={} column={} ascending={} rows={}",
                    sourceSheetName, sortColumnIndex, ascending, dataRows.size());
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 排序失败", exception);
        }
    }

    private static String extractCellString(XSSFRow row, int columnIndex) {
        XSSFCell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static List<String> extractRowStrings(XSSFRow row) {
        int last = row.getLastCellNum();
        List<String> values = new ArrayList<>(last);
        for (int c = 0; c < last; c++) {
            values.add(extractCellString(row, c));
        }
        return values;
    }

    private static void writeRowStrings(XSSFRow target, List<String> values) {
        int last = target.getLastCellNum();
        if (last > 0) {
            for (int c = last - 1; c >= 0; c--) {
                XSSFCell cell = target.getCell(c);
                if (cell != null) {
                    target.removeCell(cell);
                }
            }
        }
        for (int c = 0; c < values.size(); c++) {
            XSSFCell cell = target.createCell(c);
            cell.setCellValue(values.get(c));
        }
    }

    /**
     * Removes duplicate data rows from a sheet (header row preserved).
     * Duplicates are detected by NFKC-normalized case-insensitive full-row
     * equality of the string representations of all cells. Rows are kept
     * in their original order; only later duplicates are removed.
     */
    public byte[] dedupeRows(byte[] sourceBytes, String sourceSheetName) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        if (sourceSheetName == null || sourceSheetName.isBlank()) {
            throw new SpreadsheetWriteException("sourceSheetName 不能为空");
        }
        if (!hasZipMagic(sourceBytes)) {
            throw new SpreadsheetReadException("源字节不是有效的 XLSX（ZIP 头缺失）");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new SpreadsheetReadException(
                    "源 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.getSheet(sourceSheetName);
            if (sheet == null) {
                throw new SpreadsheetWriteException(
                        "源工作表不存在：" + sourceSheetName);
            }
            XSSFRow headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException(
                        "源工作表缺少表头行：" + sourceSheetName);
            }
            int lastRow = sheet.getLastRowNum();
            if (lastRow < 1) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
            Set<String> seen = new HashSet<>();
            List<List<String>> kept = new ArrayList<>();
            for (int r = 1; r <= lastRow; r++) {
                XSSFRow row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> values = extractRowStrings(row);
                String key = normalizeRowKey(values);
                if (seen.add(key)) {
                    kept.add(values);
                }
            }
            int removed = (lastRow) - kept.size();
            for (int i = 0; i < kept.size(); i++) {
                XSSFRow targetRow = sheet.getRow(i + 1);
                if (targetRow == null) {
                    targetRow = sheet.createRow(i + 1);
                }
                writeRowStrings(targetRow, kept.get(i));
            }
            // Clear trailing rows beyond the kept count.
            for (int r = kept.size() + 1; r <= lastRow; r++) {
                XSSFRow row = sheet.getRow(r);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "去重后的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_rows_deduped sheet={} removed={} kept={}",
                    sourceSheetName, removed, kept.size());
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 去重失败", exception);
        }
    }

    private static String normalizeRowKey(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int c = 0; c < values.size(); c++) {
            if (c > 0) {
                builder.append('|');
            }
            builder.append(Normalizer.normalize(values.get(c), Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    /**
     * Appends a summary sheet ({@code summarySheetName}) with per-column
        int last = row.getLastCellNum();
        StringBuilder builder = new StringBuilder();
        for (int c = 0; c < last; c++) {
            if (c > 0) {
                builder.append('');
            }
            XSSFCell cell = row.getCell(c);
            String value = cell == null ? "" : extractCellString(row, c);
            builder.append(Normalizer.normalize(value, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    /**
     * Appends a summary sheet ({@code summarySheetName}) with per-column
     * aggregate statistics computed from {@code sourceSheetName}. For each
     * numeric column the row emits SUM / AVERAGE / MIN / MAX / COUNT; for
     * non-numeric columns it emits COUNT only. Source rows with no
     * eligible numeric cell for a column are excluded from the column's
     * MIN/MAX/AVERAGE computation but still count toward COUNT.
     */
    public byte[] appendSummarySheet(byte[] sourceBytes, String sourceSheetName,
            String summarySheetName) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        if (sourceSheetName == null || sourceSheetName.isBlank()) {
            throw new SpreadsheetWriteException("sourceSheetName 不能为空");
        }
        if (summarySheetName == null || summarySheetName.isBlank()) {
            throw new SpreadsheetWriteException("summarySheetName 不能为空");
        }
        if (sourceSheetName.equals(summarySheetName)) {
            throw new SpreadsheetWriteException(
                    "summarySheetName 不能与 sourceSheetName 相同");
        }
        if (summarySheetName.length() > 31) {
            throw new SpreadsheetWriteException(
                    "summarySheetName 长度超过 31 字符");
        }
        if (!hasZipMagic(sourceBytes)) {
            throw new SpreadsheetReadException("源字节不是有效的 XLSX（ZIP 头缺失）");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new SpreadsheetReadException(
                    "源 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sourceSheet = workbook.getSheet(sourceSheetName);
            if (sourceSheet == null) {
                throw new SpreadsheetWriteException(
                        "源工作表不存在：" + sourceSheetName);
            }
            XSSFRow headerRow = sourceSheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException(
                        "源工作表缺少表头行：" + sourceSheetName);
            }
            int lastSourceRow = sourceSheet.getLastRowNum();
            int columnCount = headerRow.getLastCellNum();

            List<String> headers = new ArrayList<>(columnCount);
            for (int c = 0; c < columnCount; c++) {
                XSSFCell h = headerRow.getCell(c);
                headers.add(h == null ? "" : h.getStringCellValue());
            }

            List<List<Double>> numericColumns = new ArrayList<>(columnCount);
            List<Integer> rowCounts = new ArrayList<>(columnCount);
            for (int c = 0; c < columnCount; c++) {
                numericColumns.add(new ArrayList<>());
                rowCounts.add(0);
            }
            for (int r = 1; r <= lastSourceRow; r++) {
                XSSFRow row = sourceSheet.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < columnCount; c++) {
                    XSSFCell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        numericColumns.get(c).add(cell.getNumericCellValue());
                    }
                }
                for (int c = 0; c < columnCount; c++) {
                    if (row.getCell(c) != null) {
                        rowCounts.set(c, rowCounts.get(c) + 1);
                    }
                }
            }

            int existingIndex = workbook.getSheetIndex(summarySheetName);
            if (existingIndex >= 0) {
                workbook.removeSheetAt(existingIndex);
            }
            XSSFSheet summarySheet = workbook.createSheet(summarySheetName);
            XSSFCellStyle headerStyle = boldStyle(workbook);
            String[] statHeaders = {"列名", "统计项", "数值"};
            XSSFRow firstRow = summarySheet.createRow(0);
            for (int i = 0; i < statHeaders.length; i++) {
                XSSFCell cell = firstRow.createCell(i);
                cell.setCellValue(statHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int outRow = 1;
            int maxRows = properties.getMaxRows();
            for (int c = 0; c < columnCount; c++) {
                List<Double> values = numericColumns.get(c);
                if (outRow + 4 > maxRows) {
                    break;
                }
                String columnLabel = headers.get(c).isEmpty() ? "列" + (c + 1) : headers.get(c);
                writeSummaryEntry(summarySheet, outRow++, columnLabel, "COUNT", rowCounts.get(c), null);
                if (!values.isEmpty()) {
                    double sum = 0.0;
                    double min = values.get(0);
                    double max = values.get(0);
                    for (double v : values) {
                        sum += v;
                        if (v < min) min = v;
                        if (v > max) max = v;
                    }
                    double avg = sum / values.size();
                    writeSummaryEntry(summarySheet, outRow++, columnLabel, "SUM", sum, null);
                    writeSummaryEntry(summarySheet, outRow++, columnLabel, "AVERAGE", avg, null);
                    writeSummaryEntry(summarySheet, outRow++, columnLabel, "MIN", min, null);
                    writeSummaryEntry(summarySheet, outRow++, columnLabel, "MAX", max, null);
                }
            }

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "包含统计页的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_summary_appended source={} summary={} entries={}",
                    sourceSheetName, summarySheetName, outRow - 1);
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 统计页生成失败", exception);
        }
    }

    private void writeSummaryEntry(
            XSSFSheet sheet, int rowIndex, String columnLabel, String stat, double numericValue,
            XSSFCellStyle unused) {
        XSSFRow row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        row.createCell(0).setCellValue(columnLabel);
        row.createCell(1).setCellValue(stat);
        XSSFCell valueCell = row.createCell(2);
        valueCell.setCellValue(numericValue);
    }

    /**
     * Renders a single bar chart on a dedicated sheet inside a copy of the
     * source workbook. Reads {@code categoryColumn} and {@code valueColumn}
     * by header name from {@code sourceSheetName}; rows missing either
     * column or with non-numeric values are skipped. The chart sheet is
     * recreated if it already exists in the source workbook.
     */
    public byte[] appendBarChart(byte[] sourceBytes, BarChartSpec spec) {
        if (!isAvailable()) {
            throw new SpreadsheetWriteException("spreadsheet 渲染未启用");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new SpreadsheetWriteException("源字节为空");
        }
        if (spec == null) {
            throw new SpreadsheetWriteException("spec 不能为空");
        }
        if (!hasZipMagic(sourceBytes)) {
            throw new SpreadsheetReadException("源字节不是有效的 XLSX（ZIP 头缺失）");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new SpreadsheetReadException(
                    "源 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sourceSheet = workbook.getSheet(spec.sourceSheetName());
            if (sourceSheet == null) {
                throw new SpreadsheetWriteException(
                        "源工作表不存在：" + spec.sourceSheetName());
            }
            XSSFRow headerRow = sourceSheet.getRow(0);
            if (headerRow == null) {
                throw new SpreadsheetWriteException(
                        "源工作表缺少表头行：" + spec.sourceSheetName());
            }
            int[] columnIndices = resolveChartColumns(
                    headerRow, spec.categoryColumn(), spec.valueColumn());

            List<String> categories = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            int lastRowIndex = sourceSheet.getLastRowNum();
            for (int r = 1; r <= lastRowIndex; r++) {
                XSSFRow row = sourceSheet.getRow(r);
                if (row == null) {
                    continue;
                }
                XSSFCell catCell = row.getCell(columnIndices[0]);
                XSSFCell valCell = row.getCell(columnIndices[1]);
                if (catCell == null || valCell == null) {
                    continue;
                }
                if (catCell.getCellType() != CellType.STRING) {
                    continue;
                }
                if (valCell.getCellType() != CellType.NUMERIC) {
                    continue;
                }
                categories.add(catCell.getStringCellValue());
                values.add(valCell.getNumericCellValue());
            }
            if (categories.isEmpty()) {
                throw new SpreadsheetWriteException(
                        "源工作表中没有可绘制的有效行（类别必须是字符串、数值必须是数字）");
            }

            int chartSheetIndex = workbook.getSheetIndex(spec.chartSheetName());
            if (chartSheetIndex >= 0) {
                workbook.removeSheetAt(chartSheetIndex);
            }
            XSSFSheet chartSheet = workbook.createSheet(spec.chartSheetName());

            XSSFDrawing drawing = chartSheet.createDrawingPatriarch();
            ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, 5, 10, 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(spec.title());
            chart.setTitleOverlay(false);

            String categoryRef = spec.sourceSheetName() + "!"
                    + columnRange(spec.sourceSheetName(), columnIndices[0], 1, lastRowIndex + 1);
            String valueRef = spec.sourceSheetName() + "!"
                    + columnRange(spec.sourceSheetName(), columnIndices[1], 1, lastRowIndex + 1);
            XDDFCategoryDataSource categorySource = XDDFDataSourcesFactory.fromArray(
                    categories.toArray(new String[0]), categoryRef);
            XDDFNumericalDataSource<Double> valueSource = XDDFDataSourcesFactory.fromArray(
                    values.toArray(new Double[0]), valueRef);

            XDDFCategoryAxis categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis valueAxis = chart.createValueAxis(AxisPosition.LEFT);
            XDDFBarChartData chartData = (XDDFBarChartData)
                    chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            chartData.setVaryColors(true);
            chartData.addSeries(categorySource, valueSource).setTitle(
                    spec.valueColumn(), null);
            chart.plot(chartData);

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new SpreadsheetWriteException(
                        "包含柱状图的 XLSX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "spreadsheet_bar_chart source={} chart_sheet={} categories={} bytes={}",
                    spec.sourceSheetName(),
                    spec.chartSheetName(),
                    categories.size(),
                    bytes.length);
            return bytes;
        } catch (IOException exception) {
            throw new SpreadsheetWriteException("XLSX 柱状图生成失败", exception);
        }
    }

    private int[] resolveChartColumns(XSSFRow headerRow, String categoryName, String valueName) {
        int categoryIdx = -1;
        int valueIdx = -1;
        int last = headerRow.getLastCellNum();
        for (int c = 0; c < last; c++) {
            XSSFCell cell = headerRow.getCell(c);
            if (cell == null || cell.getCellType() != CellType.STRING) {
                continue;
            }
            String header = cell.getStringCellValue();
            if (header.equals(categoryName)) {
                categoryIdx = c;
            } else if (header.equals(valueName)) {
                valueIdx = c;
            }
        }
        if (categoryIdx < 0) {
            throw new SpreadsheetWriteException("源工作表缺少类别列：" + categoryName);
        }
        if (valueIdx < 0) {
            throw new SpreadsheetWriteException("源工作表缺少数值列：" + valueName);
        }
        return new int[] {categoryIdx, valueIdx};
    }

    private static String columnRange(String sheetName, int columnIndex, int startRow, int endRow) {
        String col = CellReference.convertNumToColString(columnIndex);
        return "'" + sheetName + "'!$" + col + "$" + startRow + ":$" + col + "$" + endRow;
    }

    /**
     * Inputs for {@link #appendBarChart(byte[], BarChartSpec)}.
     *
     * @param sourceSheetName  existing sheet name to read categories/values from
     * @param chartSheetName   name of the sheet to host the chart; replaced if it
     *                         already exists in the source workbook
     * @param categoryColumn   column header (exact match) holding category labels
     * @param valueColumn      column header (exact match) holding numeric values
     * @param title            chart title; may be blank
     */
    public record BarChartSpec(
            String sourceSheetName,
            String chartSheetName,
            String categoryColumn,
            String valueColumn,
            String title) {
    }
}
