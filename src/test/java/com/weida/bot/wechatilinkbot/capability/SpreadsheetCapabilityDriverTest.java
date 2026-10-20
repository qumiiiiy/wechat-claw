package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Manual driver for end-to-end visual inspection.
 *
 * <p>Run with:</p>
 * <pre>
 * .\mvnw.cmd -Dtest=SpreadsheetCapabilityDriverTest test
 * </pre>
 *
 * <p>The generated workbook is written under
 * {@code ./build/spreadsheet-demo/} relative to the working directory so it
 * persists after the test exits. Open the printed path in Excel or WPS to
 * verify the visual layout.</p>
 *
 * <p>The test reopens the workbook through POI after writing so the console
 * report is independent of the capability's own verify step.</p>
 *
 * <p>This class lives under {@code src/test/java} so it satisfies the
 * project-wide {@code *Test.java} naming convention enforced by
 * {@code ArchitectureRuleTest.testClassNamesUseTheSingularTestSuffix}.</p>
 */
class SpreadsheetCapabilityDriverTest {

    private static final Path DEMO_OUTPUT = Paths.get("build", "spreadsheet-demo");

    @Test
    void driverWritesAnOpenableWorkbookForManualInspection() throws IOException {
        Files.createDirectories(DEMO_OUTPUT);

        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(DEMO_OUTPUT);

        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        SpreadsheetCapability capability = new SpreadsheetCapability(renderer, properties);

        SpreadsheetRequest request = new SpreadsheetRequest(
                "本周课程任务清单",
                "本周任务",
                List.of(
                        SpreadsheetColumn.of("序号"),
                        SpreadsheetColumn.of("课程"),
                        SpreadsheetColumn.of("任务"),
                        SpreadsheetColumn.of("截止"),
                        SpreadsheetColumn.of("状态")),
                List.of(
                        List.of("1", "算法", "完成第三章练习", "2026-08-02", "进行中"),
                        List.of("2", "英语", "阅读 Unit 5", "2026-08-03", "未开始"),
                        List.of("3", "操作系统", "Lab 2 报告", "2026-08-05", "未开始"),
                        List.of("4", "机器学习", "Kaggle 入门赛", "2026-08-08", "未开始")));

        SpreadsheetResult result = capability.generate(request);

        System.out.println();
        System.out.println("=== Spreadsheet demo output ===");
        System.out.println("absolutePath : " + result.filePath().toAbsolutePath());
        System.out.println("sheet        : " + result.sheetName());
        System.out.println("headers      : " + result.verifiedHeaders());
        System.out.println("dataRowCount : " + result.verifiedRowCount());
        System.out.println("bytes        : " + result.bytes().length);
        System.out.println();

        // Independent reopen to demonstrate that the bytes are well-formed.
        try (InputStream input = Files.newInputStream(result.filePath());
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            System.out.println("reopened      : OK");
            System.out.println("sheetName     : " + workbook.getSheetName(0));
            System.out.println("rows[1][2]    : "
                    + workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            System.out.println("rows[3][1]    : "
                    + workbook.getSheetAt(0).getRow(3).getCell(1).getStringCellValue());
        }

        System.out.println();
        System.out.println("Open the file above in Excel or WPS to inspect visually.");
    }
}