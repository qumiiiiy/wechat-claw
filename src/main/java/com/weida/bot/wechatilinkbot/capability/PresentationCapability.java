package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.pptx.PptxDocumentClient;
import com.weida.bot.wechatilinkbot.infrastructure.pptx.PptxDocumentClient.PresentationGenerationResult;
import com.weida.bot.wechatilinkbot.infrastructure.pptx.PptxDocumentProperties;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Facade in front of {@link PptxDocumentClient}. Exposes only whole-deck
 * generation; element-level editing is intentionally out of scope. The
 * agent tool layer wires the {@code GENERATE_PRESENTATION} tool onto
 * this capability.
 */
@Component
public class PresentationCapability {

    private final PptxDocumentClient client;
    private final PptxDocumentProperties properties;

    public PresentationCapability(
            PptxDocumentClient client, PptxDocumentProperties properties) {
        this.client = Objects.requireNonNull(client, "client 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    public boolean available() {
        return properties.isEnabled() && client.isAvailable();
    }

    public GeneratedMedia generate(PresentationRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        if (!available()) {
            throw new IllegalStateException("PPTX 渲染未启用");
        }
        PresentationGenerationResult result = client.generate(request);
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                result.bytes(),
                result.fileName(),
                0L,
                "");
    }
}