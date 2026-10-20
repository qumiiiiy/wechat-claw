package com.weida.bot.wechatilinkbot.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, validated input for one structured DOCX render.
 *
 * <p>The renderer is given the request as-is; the capability layer owns
 * filesystem I/O and POI re-read verification.</p>
 *
 * @param title    optional document title; rendered as the centered cover title
 * @param sections ordered list of body sections; must be non-empty
 */
public record DocumentRequest(String title, List<DocumentSection> sections) {

    public DocumentRequest {
        if (title != null) {
            title = Normalizer.normalize(title.strip(), Normalizer.Form.NFKC);
            if (title.isEmpty()) {
                title = null;
            }
        }
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("sections 不能为空");
        }
        List<DocumentSection> copy = new ArrayList<>(sections.size());
        for (DocumentSection section : sections) {
            Objects.requireNonNull(section, "section 不能为 null");
            copy.add(section);
        }
        sections = Collections.unmodifiableList(copy);
    }
}