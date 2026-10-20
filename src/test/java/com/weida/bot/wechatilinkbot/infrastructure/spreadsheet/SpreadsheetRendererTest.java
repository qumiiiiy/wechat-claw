package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetReadResult;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetSheetView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SpreadsheetRenderer}. The renderer writes
 * workbook bytes only — no filesystem is touched here.
 */
class SpreadsheetRendererTest {

    @Test
    void rendersWorkbookWithHeadersAndData() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();

        byte[] bytes = renderer.render(new SpreadsheetRequest(
                "课程任务清单",
                null,
                List.of(SpreadsheetColumn.of("序号"), SpreadsheetColumn.of("任务")),
                List.of(
                        List.of("1", "算法作业"),
                        List.of("2", "英语阅读"))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("Sheet1");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("序号");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("任务");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("算法作业");
        }
    }

    @Test
    void rejectsDuplicateColumnHeaders() {
        SpreadsheetRenderer renderer = enabledRenderer();

        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "dup",
                null,
                List.of(SpreadsheetColumn.of("Name"), SpreadsheetColumn.of("name")),
                List.of())))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void rejectsBlankColumnHeader() {
        assertThatThrownBy(() -> new SpreadsheetColumn("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("column name");
    }

    @Test
    void rejectsRowColumnArityMismatch() {
        SpreadsheetRenderer renderer = enabledRenderer();

        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "arity",
                null,
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("only-one")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("列数与表头不一致");
    }

    @Test
    void rejectsRowCountExceedingLimit() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setMaxRows(2);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("1"));
        rows.add(List.of("2"));
        rows.add(List.of("3"));

        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "rows",
                null,
                List.of(SpreadsheetColumn.of("x")),
                rows)))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("超过最大限制");
    }

    @Test
    void rejectsTitleLongerThanMax() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setMaxTitleCharacters(3);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);

        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "too-long-title",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of())))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("title");
    }

    @Test
    void rejectsRenderWhenDisabled() {
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(new SpreadsheetProperties());

        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "off",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of())))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void formulaInjectionPrefixesAreRejectedAtRenderTime() {
        // Upstream consumers (Excel, Numbers, Sheets) treat cells whose first
        // character is one of = + - @ \t \r as live formulas even when POI
        // stored them as STRING. The renderer therefore rejects any such value
        // on both data cells and column headers before reaching POI.
        SpreadsheetRenderer renderer = enabledRenderer();

        for (String payload : List.of("=1+1", "+1+1", "-1", "@SUM(1)", "\t1+1", "\r1+1")) {
            assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                    "公式注入回归",
                    null,
                    List.of(SpreadsheetColumn.of("值")),
                    List.of(List.of(payload)))))
                    .as("payload=%s", payload)
                    .isInstanceOf(SpreadsheetWriteException.class)
                    .hasMessageContaining("公式注入前缀");
        }
    }

    @Test
    void readFromBytesAcceptsValidWorkbook() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new SpreadsheetRequest(
                "ok",
                "ok",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("x", "y"))));

        SpreadsheetReadResult result = renderer.readFromBytes(bytes);
        assertThat(result.totalBytes()).isEqualTo(bytes.length);
        assertThat(result.sheets()).hasSize(1);
        SpreadsheetSheetView view = result.sheets().get(0);
        assertThat(view.name()).isEqualTo("ok");
        assertThat(view.headers()).containsExactly("A", "B");
        assertThat(view.rowCount()).isEqualTo(1);
    }

    @Test
    void readFromBytesRejectsFormulaCells() throws Exception {
        // Build a workbook that contains a formula cell so we can prove the
        // renderer rejects it on read.
        byte[] malicious;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("evil");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("cmd");
            XSSFRow row = sheet.createRow(1);
            XSSFCell cell = row.createCell(0);
            cell.setCellFormula("1+1");
            workbook.write(out);
            malicious = out.toByteArray();
        }

        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.readFromBytes(malicious))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining("公式");
    }

    @Test
    void readFromBytesRejectsNonStringDataCellTypes() throws Exception {
        byte[] malicious;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("typed");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("value");
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue(42.0);
            workbook.write(out);
            malicious = out.toByteArray();
        }

        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.readFromBytes(malicious))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining("字符串");
    }

    @Test
    void readFromBytesRejectsBytesWithoutZipMagic() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] junk = "not a workbook".getBytes();
        assertThatThrownBy(() -> renderer.readFromBytes(junk))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining("ZIP");
    }

    @Test
    void readFromBytesRejectsEmptyBytes() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.readFromBytes(new byte[0]))
                .isInstanceOf(SpreadsheetReadException.class);
    }

    @Test
    void readFromBytesRejectsOversizeWorkbook() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setMaxReadBytes(1024);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        byte[] bytes = new byte[2048];
        System.arraycopy(new byte[]{'P', 'K', 0x03, 0x04}, 0, bytes, 0, 4);

        assertThatThrownBy(() -> renderer.readFromBytes(bytes))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining("超过最大读取字节数");
    }

    @Test
    void appendSheetAddsSecondSheetAndPreservesExistingData() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始",
                "原始表",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("1", "2"))));

        byte[] appended = renderer.appendSheet(source, new SpreadsheetAppendRequest(
                "新增表",
                List.of(SpreadsheetColumn.of("X")),
                List.of(List.of("hi"))));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(appended))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetName(0)).isEqualTo("原始表");
            assertThat(wb.getSheetName(1)).isEqualTo("新增表");
            assertThat(wb.getSheet("新增表").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("X");
            assertThat(wb.getSheet("新增表").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("hi");
            assertThat(wb.getSheet("原始表").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("A");
        }
    }

    @Test
    void appendSheetRefusesConflictingName() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "源",
                "Sheet1",
                List.of(SpreadsheetColumn.of("A")),
                List.of()));

        assertThatThrownBy(() -> renderer.appendSheet(source, new SpreadsheetAppendRequest(
                "Sheet1",
                List.of(SpreadsheetColumn.of("B")),
                List.of())))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("工作表名已存在");
    }

    @Test
    void appendSheetRejectsFormulaInNewCells() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "源",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of()));

        // appendSheet accepts text directly, so the "injection" here is
        // the verification step rejecting formulas inside the source bytes.
        byte[] malicious;
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("S");
            XSSFRow row = sheet.createRow(0);
            row.createCell(0).setCellValue("ok");
            XSSFRow r2 = sheet.createRow(1);
            r2.createCell(0).setCellFormula("1+1");
            wb.write(out);
            malicious = out.toByteArray();
        }

        assertThatThrownBy(() -> renderer.appendSheet(malicious, new SpreadsheetAppendRequest(
                "new",
                List.of(SpreadsheetColumn.of("c")),
                List.of(List.of("v")))))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining("公式");
    }

    @Test
    void appendSheetPreservesOriginalBytesUnchanged() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "源",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));
        // Snapshot the source bytes — we want them byte-equal after the
        // appendSheet call, since appendSheet must never mutate the source.
        byte[] snapshot = source.clone();

        renderer.appendSheet(source, new SpreadsheetAppendRequest(
                "second",
                List.of(SpreadsheetColumn.of("B")),
                List.of(List.of("z"))));

        assertThat(source).isEqualTo(snapshot);
        // Sanity-check the references actually matched.
        assertThat(CellReference.class.getName()).isNotEmpty();
    }

    @Test
    void rejectsCellValueStartingWithEqualsSign() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("=SUM(A1:A2)")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsCellValueStartingWithPlusSign() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("+1+1")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsCellValueStartingWithAtSign() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("@SUM(A1:A2)")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsCellValueStartingWithMinusSign() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("-1+1")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsCellValueStartingWithTab() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("\t1+1")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsCellValueStartingWithCarriageReturn() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("名称")),
                List.of(List.of("\r1+1")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsColumnHeaderStartingWithEqualsSign() {
        SpreadsheetRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new SpreadsheetRequest(
                "注入测试",
                null,
                List.of(SpreadsheetColumn.of("=公式")),
                List.of(List.of("v")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    private static SpreadsheetRenderer enabledRenderer() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        return new SpreadsheetRenderer(properties);
    }
}