package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiagramPropertiesTest {

    @Test
    void keepsAConfiguredBatchLauncherPathUnchanged() {
        DiagramProperties properties = new DiagramProperties();
        properties.setExecutable("plantuml.bat");

        assertThat(properties.getExecutable()).isEqualTo("plantuml.bat");
    }

    @Test
    void keepsAnUnresolvedExecutableValueForAvailabilityDiagnostics() {
        DiagramProperties properties = new DiagramProperties();

        properties.setExecutable("missing-plantuml");

        assertThat(properties.getExecutable()).isEqualTo("missing-plantuml");
    }
}
