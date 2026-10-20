package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextFileIntentDetectorTest {

    private final TextFileIntentDetector detector = new TextFileIntentDetector();

    @Test
    void recognizesExplicitFileDeliveryRequests() {
        assertThat(detector.hasIntent("生成一个 Markdown 计划文件")).isTrue();
        assertThat(detector.hasIntent("把这段内容整理成 report.csv 文件")).isTrue();
        assertThat(detector.hasIntent("请给我一份配置文档")).isTrue();
        assertThat(detector.hasIntent("把去西湖的流程整理成详细文字变成 PDF")).isTrue();
    }

    @Test
    void doesNotTreatFileQuestionsOrSummariesAsGeneration() {
        assertThat(detector.hasIntent("如何生成 CSV 文件？")).isFalse();
        assertThat(detector.hasIntent("请总结我上传的这个文件")).isFalse();
        assertThat(detector.hasIntent("Markdown 是什么？")).isFalse();
    }

    @Test
    void doesNotTreatFileBrowsingRequestsAsGeneration() {
        assertThat(detector.hasIntent("给我看下D盘的exe文件是啥")).isFalse();
        assertThat(detector.hasIntent("D盘下面有没有exe文件 有的话告诉我他是啥")).isFalse();
        assertThat(detector.hasIntent("给我查一下D盘有哪些文件")).isFalse();
        assertThat(detector.hasIntent("帮我找一下exe文件")).isFalse();
    }

    @Test
    void distinguishesRenderedDocumentsFromPlainTextFiles() {
        assertThat(detector.hasDocumentIntent("生成一份项目报告.pdf")).isTrue();
        assertThat(detector.hasDocumentIntent("制作一个 Word 文档")).isTrue();
        assertThat(detector.hasDocumentIntent("生成一个 Markdown 计划文件")).isFalse();
        assertThat(detector.hasDocumentIntent("把数据整理成 report.csv 文件")).isFalse();
    }
}
