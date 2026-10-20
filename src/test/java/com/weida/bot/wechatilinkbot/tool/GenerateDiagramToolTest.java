package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.DiagramCapability;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramErrorCode;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramException;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramProperties;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramRenderer;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.DiagramToolInput;
import org.junit.jupiter.api.Test;

class GenerateDiagramToolTest {

    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x10, 0x20, 0x30, 0x40
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsToolMetadata() {
        GenerateDiagramTool tool = tool(true, false);

        assertThat(tool.name()).isEqualTo("generate_diagram");
        assertThat(tool.inputType()).isEqualTo(DiagramToolInput.class);
        assertThat(tool.available()).isTrue();
        assertThat(tool.availabilityReason()).isEqualTo("available");
        assertThat(tool.producesMedia()).isTrue();
        assertThat(tool.returnDirect()).isTrue();
    }

    @Test
    void reportsDisabledWhenCapabilityIsOff() {
        GenerateDiagramTool tool = tool(false, false);

        assertThat(tool.available()).isFalse();
        assertThat(tool.availabilityReason()).isEqualTo("disabled");
    }

    @Test
    void executesAndReturnsImageMediaWithStableJson() throws Exception {
        GenerateDiagramTool tool = tool(true, false);

        ToolExecution execution = tool.execute(
                new DiagramToolInput(
                        " Sequence ",
                        "@startuml\nAlice -> Bob\n@enduml"),
                new ToolContext(
                        "wechat-ilink|user-1",
                        "alice -> bob",
                        false,
                        false,
                        false));

        JsonNode payload = objectMapper.readTree(execution.content());
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("media_type").asText()).isEqualTo("image");
        assertThat(payload.path("diagram_type").asText()).isEqualTo("sequence");
        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().kind()).isEqualTo(GeneratedMediaKind.IMAGE);
        assertThat(execution.generatedMedia().fileName()).isEqualTo("plantuml-sequence.png");
        assertThat(execution.generatedMedia().bytes()).containsExactly(PNG_BYTES);
    }

    @Test
    void propagatesForbiddenDirectiveWithoutFallback() {
        DiagramCapability capability = failingCapability(
                DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE);
        MediaCapability mediaCapability = mock(MediaCapability.class);
        when(mediaCapability.isImageAvailable()).thenReturn(true);

        GenerateDiagramTool tool = new GenerateDiagramTool(capability, mediaCapability, objectMapper);

        try {
            tool.execute(new DiagramToolInput("sequence",
                    "@startuml\nA -> B\n@enduml"),
                    new ToolContext("wechat-ilink|user-1", "x",
                            false, false, false));
            org.junit.jupiter.api.Assertions.fail("expected DiagramException");
        } catch (DiagramException exception) {
            assertThat(exception.getCode())
                    .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE);
        }
        // FORBIDDEN_DIRECTIVE is a source quality problem, not a PlantUML
        // reliability problem — falling back to qwen-image would not help.
        verify(mediaCapability, never()).generateImage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void fallsBackToQwenImageOnTimeout() throws Exception {
        DiagramCapability capability = failingCapability(
                DiagramErrorCode.DIAGRAM_TIMEOUT);
        MediaCapability mediaCapability = mock(MediaCapability.class);
        when(mediaCapability.isImageAvailable()).thenReturn(true);
        GeneratedMedia fallback = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                PNG_BYTES,
                "qwen-image.png",
                0L,
                "");
        when(mediaCapability.generateImage(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(fallback);

        GenerateDiagramTool tool = new GenerateDiagramTool(capability, mediaCapability, objectMapper);

        ToolExecution execution = tool.execute(
                new DiagramToolInput("mindmap",
                        "@startmindmap\n* Root\n@endmindmap"),
                new ToolContext("wechat-ilink|user-1", "高一数学思维导图",
                        false, false, false));

        JsonNode payload = objectMapper.readTree(execution.content());
        assertThat(payload.path("status").asText()).isEqualTo("fallback");
        assertThat(payload.path("original_error_code").asText())
                .isEqualTo(DiagramErrorCode.DIAGRAM_TIMEOUT);
        assertThat(execution.generatedMedia().fileName()).isEqualTo("qwen-image.png");
    }

    @Test
    void rethrowsOriginalErrorWhenQwenImageFallbackFails() {
        DiagramCapability capability = failingCapability(
                DiagramErrorCode.DIAGRAM_OUTPUT_INVALID);
        MediaCapability mediaCapability = mock(MediaCapability.class);
        when(mediaCapability.isImageAvailable()).thenReturn(true);
        when(mediaCapability.generateImage(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("qwen-image failed"));

        GenerateDiagramTool tool = new GenerateDiagramTool(capability, mediaCapability, objectMapper);

        try {
            tool.execute(new DiagramToolInput("flowchart",
                    "@startuml\nA -> B\n@enduml"),
                    new ToolContext("wechat-ilink|user-1", "x",
                            false, false, false));
            org.junit.jupiter.api.Assertions.fail("expected DiagramException");
        } catch (DiagramException exception) {
            assertThat(exception.getCode())
                    .isEqualTo(DiagramErrorCode.DIAGRAM_OUTPUT_INVALID);
        }
    }

    @Test
    void rethrowsOriginalErrorWhenImageCapabilityDisabled() {
        DiagramCapability capability = failingCapability(
                DiagramErrorCode.DIAGRAM_OUTPUT_INVALID);
        MediaCapability mediaCapability = mock(MediaCapability.class);
        when(mediaCapability.isImageAvailable()).thenReturn(false);

        GenerateDiagramTool tool = new GenerateDiagramTool(capability, mediaCapability, objectMapper);

        try {
            tool.execute(new DiagramToolInput("flowchart",
                    "@startuml\nA -> B\n@enduml"),
                    new ToolContext("wechat-ilink|user-1", "x",
                            false, false, false));
            org.junit.jupiter.api.Assertions.fail("expected DiagramException");
        } catch (DiagramException exception) {
            assertThat(exception.getCode())
                    .isEqualTo(DiagramErrorCode.DIAGRAM_OUTPUT_INVALID);
        }
    }

    private static GenerateDiagramTool tool(boolean available, boolean imageFallbackEnabled) {
        return buildTool(available, imageFallbackEnabled,
                new DiagramProperties(), new ObjectMapper());
    }

    private static GenerateDiagramTool buildTool(
            boolean available,
            boolean imageFallbackEnabled,
            DiagramProperties properties,
            ObjectMapper mapper) {
        properties.setEnabled(true);
        DiagramRenderer renderer = new DiagramRenderer(properties);
        DiagramCapability capability = new DiagramCapability(properties, renderer) {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String availabilityReason() {
                return available ? "available" : "disabled";
            }

            @Override
            public GeneratedMedia generateDiagram(String diagramType, String source) {
                return new GeneratedMedia(
                        GeneratedMediaKind.IMAGE,
                        PNG_BYTES,
                        "plantuml-" + diagramType + ".png",
                        0L,
                        "");
            }
        };
        MediaCapability mediaCapability = mock(MediaCapability.class);
        when(mediaCapability.isImageAvailable()).thenReturn(imageFallbackEnabled);
        return new GenerateDiagramTool(capability, mediaCapability, mapper);
    }

    private static DiagramCapability failingCapability(String errorCode) {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        DiagramRenderer renderer = new DiagramRenderer(properties);
        return new DiagramCapability(properties, renderer) {
            @Override
            public GeneratedMedia generateDiagram(String diagramType, String source) {
                throw new DiagramException(errorCode, "test failure: " + errorCode);
            }
        };
    }
}