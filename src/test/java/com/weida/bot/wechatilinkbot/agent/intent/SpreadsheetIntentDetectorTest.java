package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpreadsheetIntentDetectorTest {

    private final SpreadsheetIntentDetector detector = new SpreadsheetIntentDetector();

    @Test
    void recognizesExplicitSpreadsheetCreationRequests() {
        assertThat(detector.hasIntent("帮我生成一份本周作业清单 Excel")).isTrue();
        assertThat(detector.hasIntent("把这些数据整理成电子表格")).isTrue();
        assertThat(detector.hasIntent("导出为 .xlsx 文件")).isTrue();
        assertThat(detector.hasIntent("Create an XLSX workbook")).isTrue();
    }

    @Test
    void rejectsInformationQueriesAndUnrelatedExcelMentions() {
        assertThat(detector.hasIntent("Excel 怎么用？")).isFalse();
        assertThat(detector.hasIntent("是否支持生成 Excel？")).isFalse();
        assertThat(detector.hasIntent("分析一下这个 Excel")).isFalse();
        assertThat(detector.hasIntent("把现有 Excel 发邮件")).isFalse();
    }

    @Test
    void honorsNegationAndBlankInput() {
        assertThat(detector.hasIntent("不要生成 Excel，直接给我文本")).isFalse();
        assertThat(detector.hasIntent(null)).isFalse();
        assertThat(detector.hasIntent("   ")).isFalse();
    }

    @Test
    void distinguishesGenerateReadAndAppendOperations() {
        assertThat(detector.detect("生成一份课程清单 Excel"))
                .isEqualTo(SpreadsheetOperation.GENERATE);
        assertThat(detector.detect("读取这个 Excel 的内容"))
                .isEqualTo(SpreadsheetOperation.READ);
        assertThat(detector.detect("在刚才的 Excel 里增加一个工作表"))
                .isEqualTo(SpreadsheetOperation.APPEND);
        assertThat(detector.detect("给我讲个笑话"))
                .isEqualTo(SpreadsheetOperation.NONE);
    }
}
