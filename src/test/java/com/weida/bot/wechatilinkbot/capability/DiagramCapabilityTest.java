package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramErrorCode;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramException;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramProperties;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.RenderResult;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import org.junit.jupiter.api.Test;

class DiagramCapabilityTest {

    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            1, 2, 3, 4
    };

    @Test
    void availabilityReasonReportsDisabledWhenConfigurationIsOff() {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(false);
        DiagramRenderer renderer = new DiagramRenderer(properties);
        DiagramCapability capability = new DiagramCapability(properties, renderer);

        assertThat(capability.isAvailable()).isFalse();
        assertThat(capability.availabilityReason()).isEqualTo("disabled");
    }

    @Test
    void availabilityReasonReportsMissingExecutableWhenRendererCannotRun() {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        properties.setExecutable("");
        DiagramRenderer renderer = new DiagramRenderer(properties);
        DiagramCapability capability = new DiagramCapability(properties, renderer);

        assertThat(capability.isAvailable()).isFalse();
        assertThat(capability.availabilityReason()).isEqualTo("executable_missing");
    }

    @Test
    void rejectsSourceContainingPreprocessorDirective() {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        properties.setExecutable("plantuml-stub");
        FakeRenderer renderer = new FakeRenderer(PNG_BYTES, true);
        DiagramCapability capability = new DiagramCapability(properties, renderer);

        assertThatThrownBy(() -> capability.generateDiagram("sequence",
                "@startuml\n!include /etc/passwd\nA -> B\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
        assertThat(renderer.invocationCount()).isZero();
    }

    @Test
    void returnsGeneratedImageForValidSource() {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        properties.setExecutable("plantuml-stub");
        FakeRenderer renderer = new FakeRenderer(PNG_BYTES, true);
        DiagramCapability capability = new DiagramCapability(properties, renderer);

        GeneratedMedia media = capability.generateDiagram("Sequence",
                "@startuml\nAlice -> Bob\n@enduml");

        assertThat(media.kind()).isEqualTo(GeneratedMediaKind.IMAGE);
        assertThat(media.fileName()).isEqualTo("plantuml-sequence.png");
        assertThat(media.bytes()).containsExactly(PNG_BYTES);
        assertThat(renderer.invocationCount()).isEqualTo(1);
    }

    /**
     * Test double that returns a fixed payload without spawning a process.
     * The renderer is package-private; this subclass lives in the same
     * package so we can override {@code isAvailable()} and {@code render()}.
     */
    static final class FakeRenderer extends DiagramRenderer {

        private final byte[] pngBytes;
        private final boolean available;
        private int invocationCount;

        FakeRenderer(byte[] pngBytes, boolean available) {
            super(new DiagramProperties());
            this.pngBytes = pngBytes;
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public RenderResult render(String source) {
            invocationCount++;
            return new RenderResult(pngBytes, "diagram.png");
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}