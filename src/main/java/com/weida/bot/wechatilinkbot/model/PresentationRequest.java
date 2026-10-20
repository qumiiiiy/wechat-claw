package com.weida.bot.wechatilinkbot.model;

import java.util.List;
import java.util.Objects;

/**
 * Minimal PPTX creation request. Five layouts are supported, in fixed
 * order, by the renderer:
 *
 * <ol>
 *   <li>Title slide — title + subtitle.</li>
 *   <li>Bullet slide — heading + bullet list.</li>
 *   <li>Picture slide — heading + caption + picture path (server-owned).</li>
 *   <li>Table slide — heading + table rows.</li>
 *   <li>Ending slide — single "thank you" message.</li>
 * </ol>
 *
 * <p>The renderer does not enforce a 1-of-each-layout contract; callers can
 * repeat any layout as long as the per-slide data validates. The model
 * explicitly does not accept element-level editing operations; it is
 * for whole-document regeneration only.</p>
 *
 * @param title  deck title; also used to derive the safe file basename
 * @param slides ordered slides; at least one required
 */
public record PresentationRequest(
        String title,
        List<PresentationSlide> slides) {

    public PresentationRequest {
        Objects.requireNonNull(title, "title 不能为空");
        title = title.strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (slides == null || slides.isEmpty()) {
            throw new IllegalArgumentException("slides 不能为空");
        }
        slides = List.copyOf(slides);
    }
}