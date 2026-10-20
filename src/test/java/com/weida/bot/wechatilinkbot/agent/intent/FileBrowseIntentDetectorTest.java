package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileBrowseIntentDetectorTest {

    private final FileBrowseIntentDetector detector = new FileBrowseIntentDetector();

    @Test
    void recognizesDiskFileBrowseRequests() {
        assertThat(detector.hasIntent("D盘下面有没有exe文件 有的话告诉我他是啥")).isTrue();
        assertThat(detector.hasIntent("给我看下D盘的exe文件是啥")).isTrue();
        assertThat(detector.hasIntent("D盘有什么文件")).isTrue();
        assertThat(detector.hasIntent("C盘有哪些目录")).isTrue();
        assertThat(detector.hasIntent("D:/有什么文件")).isTrue();
        assertThat(detector.hasIntent("搜索D盘的exe文件")).isTrue();
    }

    @Test
    void recognizesFileSearchRequests() {
        assertThat(detector.hasIntent("找一下exe文件")).isTrue();
        assertThat(detector.hasIntent("搜索txt文件")).isTrue();
        assertThat(detector.hasIntent("列出所有pdf文件")).isTrue();
        assertThat(detector.hasIntent("看看有没有docx文件")).isTrue();
    }

    @Test
    void recognizesAStandaloneFilenameAndDirectoryPath() {
        assertThat(detector.hasIntent("正面.jpg")).isTrue();
        assertThat(detector.hasSpecificFileOrDirectory("正面.jpg")).isTrue();
        assertThat(detector.hasIntent("在d盘的campus中的self里面")).isTrue();
        assertThat(detector.hasSpecificFileOrDirectory("在d盘的campus中的self里面")).isTrue();
        assertThat(detector.hasIntent("在D盘的campus中的self里面找文件")).isTrue();
        assertThat(detector.hasSpecificFileOrDirectory("D盘campus/self找图片")).isTrue();
        assertThat(detector.hasSpecificFileOrDirectory("D盘下的campus里的self中有什么")).isTrue();
        assertThat(detector.hasIntent("D:\\campus\\self")).isTrue();
    }

    @Test
    void leavesAnUnderspecifiedFileRequestForClarification() {
        assertThat(detector.hasIntent("帮我找一下电脑上某个文件")).isTrue();
        assertThat(detector.hasSpecificFileOrDirectory("帮我找一下电脑上某个文件")).isFalse();
    }

    @Test
    void recognizesDirectoryListingAndItsContinuation() {
        assertThat(detector.hasDirectoryListingIntent("把D盘下面的文件夹罗列一下")).isTrue();
        assertThat(detector.hasDirectoryListingContinuation("罗列一下")).isTrue();
    }

    @Test
    void doesNotMatchGenericRequests() {
        assertThat(detector.hasIntent("你好")).isFalse();
        assertThat(detector.hasIntent("今天天气怎么样")).isFalse();
        assertThat(detector.hasIntent("给我讲个笑话")).isFalse();
        assertThat(detector.hasIntent("")).isFalse();
        assertThat(detector.hasIntent(null)).isFalse();
    }

    @Test
    void recognizesOnlyExplicitOpaqueIdSelection() {
        String id = "file-01234567-89ab-cdef-0123-456789abcdef";

        assertThat(detector.hasSelectionIntent("发送 " + id)).isTrue();
        assertThat(detector.extractFileId("请把 " + id + " 发给我")).contains(id);
        assertThat(detector.hasSelectionIntent(id)).isFalse();
        assertThat(detector.hasSelectionIntent("发送 C:\\secret.txt")).isFalse();
    }
}
