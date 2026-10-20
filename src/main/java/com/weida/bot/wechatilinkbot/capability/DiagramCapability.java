package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramProperties;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.DiagramRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.PlantumlSourceGuard;
import com.weida.bot.wechatilinkbot.infrastructure.diagram.RenderResult;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Diagram capability backed by the PlantUML CLI adapter.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class DiagramCapability {

    private final DiagramProperties properties;
    private final DiagramRenderer renderer;

    public DiagramCapability(DiagramProperties properties, DiagramRenderer renderer) {
        this.properties = properties;
        this.renderer = renderer;
    }

    /**
     * Returns whether the diagram tool can be invoked for the current
     * configuration.
     */
    public boolean isAvailable() {
        return properties.isEnabled() && renderer.isAvailable();
    }

    /**
     * Returns a stable availability code usable by the Tool registry.
     */
    public String availabilityReason() {
        if (!properties.isEnabled()) {
            return "disabled";
        }
        if (!renderer.isAvailable()) {
            return "executable_missing";
        }
        return "available";
    }

    /**
     * Renders the supplied source into a channel-ready PNG.
     *
     * @param diagramType user-supplied diagram type token
     * @param source      user-supplied PlantUML source text
     * @return generated image media
     */
    public GeneratedMedia generateDiagram(String diagramType, String source) {
        PlantumlSourceGuard.validate(diagramType, source, properties);
        RenderResult payload = renderer.render(source);
        String normalizedType = diagramType.strip().toLowerCase(Locale.ROOT);
        return new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                payload.pngBytes(),
                "plantuml-" + normalizedType + ".png",
                0L,
                "");
    }
}