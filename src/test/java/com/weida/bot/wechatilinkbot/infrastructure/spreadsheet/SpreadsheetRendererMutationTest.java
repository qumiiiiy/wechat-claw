package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.SpreadsheetCellValue;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Tests for the in-place mutation helpers introduced as part of the C4
 * "modify-and-close" slice: appendRows, sortRows, dedupeRows, and
 * appendSummarySheet.
 */
class SpreadsheetRendererMutationTest {

    @Test
    void appendRowsAddsRowsAfterExisting() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始",
                "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("1", "2"), List.of("3", "4"))));

        byte[] bytes = renderer.appendRows(source, "s",
                List.of(List.of("5", "6"), List.of("7", "8")));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("s");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("5");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("8");
        }
    }

    @Test
    void appendRowsRejectsArityMismatch() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("1", "2"))));

        assertThatThrownBy(() -> renderer.appendRows(source, "s",
                List.of(List.of("only-one"))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void appendRowsRejectsMissingSheet() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        assertThatThrownBy(() -> renderer.appendRows(source, "missing",
                List.of(List.of("2"))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("源工作表不存在");
    }

    @Test
    void appendRowsRejectsFormulaInjection() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        assertThatThrownBy(() -> renderer.appendRows(source, "s",
                List.of(List.of("=SUM(A1:A2)"))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void sortRowsAscendingReordersByColumn() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("姓名"), SpreadsheetColumn.of("分数")),
                List.of(
                        List.of("Charlie", "80"),
                        List.of("Alice", "95"),
                        List.of("Bob", "70"))));

        byte[] bytes = renderer.sortRows(source, "s", 0, true);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("s");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Alice");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Bob");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Charlie");
            // Scores preserved on the same row as the name.
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("95");
        }
    }

    @Test
    void sortRowsDescendingReordersByColumn() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("score"), SpreadsheetColumn.of("name")),
                List.of(
                        List.of("30", "C"),
                        List.of("10", "A"),
                        List.of("20", "B"))));

        byte[] bytes = renderer.sortRows(source, "s", 0, false);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("s");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("C");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("B");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("A");
        }
    }

    @Test
    void sortRowsRejectsOutOfRangeColumn() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        assertThatThrownBy(() -> renderer.sortRows(source, "s", 99, true))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("超出");
    }

    @Test
    void dedupeRowsRemovesLaterDuplicates() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(
                        List.of("1", "2"),
                        List.of("1", "2"),    // dup
                        List.of("3", "4"),
                        List.of("1", "2"),    // dup
                        List.of("5", "6"))));

        byte[] bytes = renderer.dedupeRows(source, "s");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("s");
            // Header + 3 unique data rows.
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("3");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("5");
        }
    }

    @Test
    void dedupeRowsTreatsCaseInsensitively() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A")),
                List.of(
                        List.of("Alice"),
                        List.of("ALICE"),
                        List.of("alice"))));

        byte[] bytes = renderer.dedupeRows(source, "s");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheet("s");
            // Header + 1 unique row only.
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
        }
    }

    @Test
    void appendSummarySheetProducesCountsAndNumericStats() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "sales",
                List.of(SpreadsheetColumn.of("商品"), SpreadsheetColumn.of("销量")),
                null,
                List.of(
                        List.of(SpreadsheetCellValue.of("A"), SpreadsheetCellValue.ofNumber("10")),
                        List.of(SpreadsheetCellValue.of("B"), SpreadsheetCellValue.ofNumber("20")),
                        List.of(SpreadsheetCellValue.of("C"), SpreadsheetCellValue.ofNumber("5")))));

        byte[] bytes = renderer.appendSummarySheet(source, "sales", "汇总");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet summary = workbook.getSheet("汇总");
            assertThat(summary).isNotNull();
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).isEqualTo("列名");
            assertThat(summary.getRow(0).getCell(1).getStringCellValue()).isEqualTo("统计项");
            assertThat(summary.getRow(0).getCell(2).getStringCellValue()).isEqualTo("数值");
            // At minimum: 商品 COUNT (1), 销量 COUNT/SUM/AVERAGE/MIN/MAX (5)
            boolean sawCount = false;
            boolean sawSum = false;
            int lastRowIndex = summary.getLastRowNum();
            for (int r = 1; r <= lastRowIndex; r++) {
                XSSFRow row = summary.getRow(r);
                if (row == null) continue;
                String stat = row.getCell(1).getStringCellValue();
                if ("COUNT".equals(stat)) sawCount = true;
                if ("SUM".equals(stat) && "销量".equals(row.getCell(0).getStringCellValue())) {
                    assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
                    sawSum = true;
                }
            }
            assertThat(sawCount).isTrue();
            assertThat(sawSum).isTrue();
        }
    }

    @Test
    void appendSummarySheetRejectsSameSourceAndSummary() {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "原始", "s",
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        assertThatThrownBy(() -> renderer.appendSummarySheet(source, "s", "s"))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("相同");
    }

    private static SpreadsheetRenderer enabledRenderer() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        return new SpreadsheetRenderer(properties);
    }
}