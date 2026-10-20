package com.weida.bot.wechatilinkbot.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Server-controlled DOCX edit request. Only narrow edits are allowed:
 * <ul>
 *   <li>Replace the existing title with {@link #newTitle()} (optional).</li>
 *   <li>Apply a list of literal {@link TextReplacement} entries to every
 *       paragraph run (optional).</li>
 *   <li>Apply a list of structured {@link DocumentEdit} operations
 *       (append / delete paragraph, change heading level, edit table
 *       cell) (optional).</li>
 * </ul>
 *
 * <p>The {@link #trustedSourcePath()} must be a server-resolved file path;
 * the model is never allowed to supply a path directly. The capability
 * layer validates that the path is a regular file inside the trusted
 * boundary before applying any edit.</p>
 *
 * <p>At least one of {@code newTitle}, {@code replacements}, or
 * {@code edits} must be non-empty; the renderer rejects requests that
 * would produce a no-op copy.</p>
 *
 * <p>Both the new title and every replacement text are subject to the
 * formula-injection prefix check shared with the spreadsheet capability
 * so user content cannot escape into spreadsheet tools after a copy/paste
 * round trip.</p>
 */
public record DocumentEditRequest(
        Path trustedSourcePath,
        String newTitle,
        List<TextReplacement> replacements,
        List<DocumentEdit> edits) {

    public DocumentEditRequest {
        Objects.requireNonNull(trustedSourcePath, "trustedSourcePath 不能为空");
        // newTitle is preserved verbatim so the renderer-side formula-injection
        // prefix check can see the original leading characters (e.g. \t, \r).
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
        edits = edits == null ? List.of() : List.copyOf(edits);
        if ((newTitle == null || newTitle.isEmpty())
                && replacements.isEmpty()
                && edits.isEmpty()) {
            throw new IllegalArgumentException(
                    "DocumentEditRequest 必须至少包含 newTitle、replacements、edits 之一");
        }
        for (int i = 0; i < replacements.size(); i++) {
            TextReplacement r = replacements.get(i);
            Objects.requireNonNull(r, "replacements[" + i + "] 不能为空");
            if (r.find() == null || r.find().isEmpty()) {
                throw new IllegalArgumentException(
                        "replacements[" + i + "].find 不能为空");
            }
            if (r.replacement() == null) {
                throw new IllegalArgumentException(
                        "replacements[" + i + "].replacement 不能为空");
            }
        }
        for (int i = 0; i < edits.size(); i++) {
            Objects.requireNonNull(edits.get(i), "edits[" + i + "] 不能为空");
        }
    }

    /** Legacy 3-arg constructor kept for backward compatibility. */
    public DocumentEditRequest(
            Path trustedSourcePath,
            String newTitle,
            List<TextReplacement> replacements) {
        this(trustedSourcePath, newTitle, replacements, null);
    }

    /**
     * Literal-text replacement.
     *
     * @param find           exact substring to locate; must be non-empty
     * @param replacement     replacement text; may be empty
     * @param caseSensitive  if true, {@code find} matches only with identical
     *                       case; otherwise matches case-insensitively while
     *                       preserving the original casing in surrounding text
     */
    public record TextReplacement(
            String find,
            String replacement,
            boolean caseSensitive) {
    }
}
