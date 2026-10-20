package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.FileBrowseCapability;
import com.weida.bot.wechatilinkbot.capability.FileBrowseProperties;
import com.weida.bot.wechatilinkbot.tool.input.FileSelectionToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void realToolReturnsSelectedFileMedia() throws Exception {
        Path file = tempDir.resolve("演示资料.txt");
        Files.writeString(file, "demo");
        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        FileBrowseCapability capability = new FileBrowseCapability(properties);
        String id = capability.search("演示", "conversation").getFirst().fileId();
        SelectFileTool tool = new SelectFileTool(capability, new ObjectMapper());

        ToolExecution execution = tool.execute(
                new FileSelectionToolInput(id),
                new ToolContext("conversation", "发送 " + id));

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().fileName()).isEqualTo("演示资料.txt");
        assertThat(execution.generatedMedia().bytes()).containsExactly("demo".getBytes());
        assertThat(execution.content()).contains("\"status\":\"ok\"");
    }

    @Test
    void rejectsOpaqueIdFromAnotherConversation() throws Exception {
        Path file = tempDir.resolve("演示资料.txt");
        Files.writeString(file, "demo");
        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        FileBrowseCapability capability = new FileBrowseCapability(properties);
        String id = capability.search("演示", "conversation-a").getFirst().fileId();
        SelectFileTool tool = new SelectFileTool(capability, new ObjectMapper());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tool.execute(
                        new FileSelectionToolInput(id),
                        new ToolContext("conversation-b", "发送 " + id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前会话");
    }

    @Test
    void treatsUuidLikeFileIdAsOpaqueIdRatherThanNaturalIndex() throws Exception {
        Path file = tempDir.resolve("演示资料.txt");
        Files.writeString(file, "demo");
        FileBrowseProperties properties = new FileBrowseProperties();
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        FileBrowseCapability capability = new FileBrowseCapability(properties);
        String id = capability.search("演示", "conversation").getFirst().fileId();
        SelectFileTool tool = new SelectFileTool(capability, new ObjectMapper());

        ToolExecution execution = tool.execute(
                new FileSelectionToolInput(id),
                new ToolContext("conversation", "发送 " + id));

        assertThat(execution.generatedMedia().fileName()).isEqualTo("演示资料.txt");
    }
}
