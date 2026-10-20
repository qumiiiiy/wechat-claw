package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetCellValue;
import com.weida.bot.wechatilinkbot.model.SpreadsheetCellValue.Kind;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer.BarChartSpec;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Tests for typed cell rendering (NUMBER / DATE / FORMULA) and bar-chart
 * generation introduced after the V1 string-only renderer.
 */
class SpreadsheetRendererTypedTest {

    @Test
    void numberCellsAreWrittenAsNumericCells() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new SpreadsheetRequest(
                "成绩单",
                null,
                List.of(SpreadsheetColumn.of("学号"), SpreadsheetColumn.of("分数")),
                null,
                List.of(
                        List.of(SpreadsheetCellValue.of("001"), SpreadsheetCellValue.ofNumber("95")),
                        List.of(SpreadsheetCellValue.of("002"), SpreadsheetCellValue.ofNumber(80.5)))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            XSSFCell numeric = sheet.getRow(1).getCell(1);
            assertThat(numeric.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(numeric.getNumericCellValue()).isEqualTo(95.0);
        }
    }

    @Test
    void dateCellsAreWrittenAsDateCells() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new SpreadsheetRequest(
                "排期",
                null,
                List.of(SpreadsheetColumn.of("任务"), SpreadsheetColumn.of("截止")),
                null,
                List.of(List.of(
                        SpreadsheetCellValue.of("实验报告"),
                        SpreadsheetCellValue.ofDate(LocalDate.of(2026, 8, 20))))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFCell dateCell = workbook.getSheetAt(0).getRow(1).getCell(1);
            assertThat(dateCell.getCellType()).isEqualTo(CellType.NUMERIC);
            // POI stores dates as numeric; just verify the data format is non-empty.
            assertThat(dateCell.getCellStyle().getDataFormatString()).contains("yy");
        }
    }

    @Test
    void formulaCellsAreWrittenAsFormulaCells() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new SpreadsheetRequest(
                "汇总",
                null,
                List.of(SpreadsheetColumn.of("项目"), SpreadsheetColumn.of("合计")),
                null,
                List.of(
                        List.of(SpreadsheetCellValue.of("组1"), SpreadsheetCellValue.ofNumber("10")),
                        List.of(SpreadsheetCellValue.of("组2"), SpreadsheetCellValue.ofNumber("20")),
                        List.of(SpreadsheetCellValue.of("总"), SpreadsheetCellValue.ofFormula("SUM(A2:A3)")))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFCell formulaCell = workbook.getSheetAt(0).getRow(3).getCell(1);
            assertThat(formulaCell.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(formulaCell.getCellFormula()).isEqualTo("SUM(A2:A3)");
        }
    }

    @Test
    void formulaWhitelistRejectsIfFormula() {
        assertThatThrownBy(() -> SpreadsheetCellValue.ofFormula("=A1+B1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORMULA 仅支持");
    }

    @Test
    void formulaWhitelistRejectsNonSumAverageFunction() {
        assertThatThrownBy(() -> SpreadsheetCellValue.ofFormula("IF(A1>0,1,0)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORMULA 仅支持");
    }

    @Test
    void numberFactoryRejectsMalformedLiteral() {
        assertThatThrownBy(() -> SpreadsheetCellValue.ofNumber("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMBER");
    }

    @Test
    void dateFactoryRejectsMalformedLiteral() {
        assertThatThrownBy(() -> SpreadsheetCellValue.ofDate("08/20/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DATE");
    }

    @Test
    void rowsAndTypedRowsAreMutuallyExclusive() {
        assertThatThrownBy(() -> new SpreadsheetRequest(
                "t",
                null,
                List.of(SpreadsheetColumn.of("c")),
                List.of(List.of("v")),
                List.of(List.of(SpreadsheetCellValue.of("v")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("互斥");
    }

    @Test
    void appendTypedSheetAddsTypedRows() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "源",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("seed"))));

        byte[] bytes = renderer.appendSheet(source, new SpreadsheetAppendRequest(
                "typed",
                List.of(SpreadsheetColumn.of("B")),
                null,
                List.of(List.of(SpreadsheetCellValue.ofNumber("42")))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFCell cell = workbook.getSheet("typed").getRow(1).getCell(0);
            assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(cell.getNumericCellValue()).isEqualTo(42.0);
        }
    }

    @Test
    void appendBarChartCreatesChartSheet() throws Exception {
        SpreadsheetRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new SpreadsheetRequest(
                "月度销售",
                "sales",
                List.of(SpreadsheetColumn.of("月份"), SpreadsheetColumn.of("销量")),
                null,
                List.of(
                        List.of(SpreadsheetCellValue.of("1月"), SpreadsheetCellValue.ofNumber("120")),
                        List.of(SpreadsheetCellValue.of("2月"), SpreadsheetCellValue.ofNumber("180")),
                        List.of(SpreadsheetCellValue.of("3月"), SpreadsheetCellValue.ofNumber("95")))));

        byte[] bytes = renderer.appendBarChart(source,
                new BarChartSpec("sales", "Chart", "月份", "销量", "季度销量"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Chart")).isNotNull();
            assertThat(workbook.getSheet("sales")).isNotNull();
        }
    }

    @Test
    void barChartRejectsMissingCategoryColumn() {
        SpreadsheetRenderer renderer = enabledRenderer();
        SpreadsheetRequest request = new SpreadsheetRequest(
                "t",
                "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                null,
                List.of(List.of(
                        SpreadsheetCellValue.of("x"),
                        SpreadsheetCellValue.ofNumber("1"))));
        byte[] source = renderer.render(request);

        assertThatThrownBy(() -> renderer.appendBarChart(source,
                new BarChartSpec("s", "Chart", "不存在", "B", "title")))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("类别列");
    }

    @Test
    void barChartRejectsMissingValueColumn() {
        SpreadsheetRenderer renderer = enabledRenderer();
        SpreadsheetRequest request = new SpreadsheetRequest(
                "t",
                "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                null,
                List.of(List.of(
                        SpreadsheetCellValue.of("x"),
                        SpreadsheetCellValue.ofNumber("1"))));
        byte[] source = renderer.render(request);

        assertThatThrownBy(() -> renderer.appendBarChart(source,
                new BarChartSpec("s", "Chart", "A", "不存在", "title")))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("数值列");
    }

    @Test
    void barChartRejectsSourceWithoutValidRows() {
        SpreadsheetRenderer renderer = enabledRenderer();
        SpreadsheetRequest request = new SpreadsheetRequest(
                "t",
                "s",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of()); // empty rows => no chart-eligible rows
        byte[] source = renderer.render(request);

        assertThatThrownBy(() -> renderer.appendBarChart(source,
                new BarChartSpec("s", "Chart", "A", "B", "title")))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("有效行");
    }

    private static SpreadsheetRenderer enabledRenderer() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        return new SpreadsheetRenderer(properties);
    }
}