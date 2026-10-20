package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.FileBrowseCapability;
import com.weida.bot.wechatilinkbot.capability.FileBrowseProperties;
import com.weida.bot.wechatilinkbot.tool.input.FileBrowseToolInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBrowseToolTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesARecentInboundDocumentWhenTheUserSaysThisFile() throws IOException {
        Files.writeString(tempDir.resolve("message-在两岸融合发展中承担青年责任.doc"), "content");

        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setAllowedExtensions(List.of("doc"));
        properties.setMaxDepth(1);
        FileBrowseTool tool = new FileBrowseTool(
                new FileBrowseCapability(properties),
                new ObjectMapper());
        ToolContext context = new ToolContext(
                "conversation",
                "查找这个文件在我电脑上的位置",
                false,
                false,
                false,
                false,
                List.of(),
                List.of("在两岸融合发展中承担青年责任.doc"));

        ToolExecution execution = tool.execute(
                new FileBrowseToolInput(
                        null,
                        null,
                        null,
                        null,
                        null,
                        true),
                context);

        assertThat(execution.content())
                .contains("message-在两岸融合发展中承担青年责任.doc")
                .contains("\"result_count\":1");
    }

    @Test
    void recentFileReferenceWithExplicitConditionsSearchesTheDisk() throws IOException {
        Files.writeString(tempDir.resolve("cc-switch.exe"), "binary");
        Files.writeString(tempDir.resolve("other.txt"), "text");

        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setAllowedExtensions(List.of("txt", "exe"));
        properties.setMaxDepth(1);
        FileBrowseTool tool = new FileBrowseTool(
                new FileBrowseCapability(properties),
                new ObjectMapper());
        ToolContext context = new ToolContext(
                "conversation",
                "最近的50个exe文件",
                false,
                false,
                false,
                false,
                List.of(),
                List.of("some-recent-inbound.pdf"));

        ToolExecution execution = tool.execute(
                new FileBrowseToolInput(
                        List.of("cc-switch"),
                        List.of("exe"),
                        null,
                        "recent_first",
                        null,
                        true),
                context);

        assertThat(execution.content())
                .contains("cc-switch.exe")
                .doesNotContain("other.txt")
                .contains("\"result_count\":1");
    }

    @Test
    void searchesAnExplicitFilenameWithoutDroppingItsExtension() throws IOException {
        Files.writeString(tempDir.resolve("message-在两岸融合发展中承担青年责任.doc"), "content");

        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setAllowedExtensions(List.of("doc"));
        properties.setMaxDepth(1);
        FileBrowseTool tool = new FileBrowseTool(
                new FileBrowseCapability(properties),
                new ObjectMapper());

        ToolExecution execution = tool.execute(
                new FileBrowseToolInput(
                        List.of("在两岸融合发展中承担青年责任.doc"),
                        null,
                        null,
                        null,
                        null,
                        false),
                new ToolContext("conversation", "在两岸融合发展中承担青年责任.doc"));

        assertThat(execution.content())
                .contains("message-在两岸融合发展中承担青年责任.doc")
                .contains("\"result_count\":1");
    }

    @Test
    void listsDirectoriesWhenTheUserAsksToEnumerateFolders() throws IOException {
        Files.createDirectories(tempDir.resolve("campus").resolve("self"));
        Files.createDirectories(tempDir.resolve("downloads"));

        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setMaxDepth(3);
        FileBrowseTool tool = new FileBrowseTool(
                new FileBrowseCapability(properties),
                new ObjectMapper());

        ToolExecution execution = tool.execute(
                new FileBrowseToolInput(
                        null,
                        null,
                        null,
                        null,
                        true,
                        false),
                new ToolContext("conversation", "把文件夹罗列一下"));

        assertThat(execution.content())
                .contains("\"entry_type\":\"directory\"")
                .contains("campus", "downloads", "self")
                .contains("\"result_count\":3");
    }

    @Test
    void searchesByMultipleSemanticNameFragments() throws IOException {
        Files.writeString(tempDir.resolve("课程表2026.xlsx"), "content1");
        Files.writeString(tempDir.resolve("课表.pdf"), "content2");
        Files.writeString(tempDir.resolve("作业.docx"), "content3");

        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setAllowedExtensions(List.of("xlsx", "pdf", "docx"));
        properties.setMaxDepth(1);
        FileBrowseTool tool = new FileBrowseTool(
                new FileBrowseCapability(properties),
                new ObjectMapper());

        ToolExecution execution = tool.execute(
                new FileBrowseToolInput(
                        List.of("课表", "课程表"),
                        null,
                        null,
                        null,
                        null,
                        false),
                new ToolContext("conversation", "找课程表或课表文件"));

        assertThat(execution.content())
                .contains("课程表2026.xlsx", "课表.pdf")
                .doesNotContain("作业.docx")
                .contains("\"result_count\":2");
    }
}
