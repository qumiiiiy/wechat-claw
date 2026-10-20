package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * Single column definition accepted by {@link SpreadsheetRequest}.
 *
 * <p>Only the column name is honored today; the type token is reserved for
 * future numeric/date rendering and validated defensively but not interpreted.
 * </p>
 *
 * <p>Instances are immutable. The supplying layer MUST treat the column name
 * as untrusted input: the rendering capability enforces emptiness, length, and
 * uniqueness before invoking the renderer.</p>
 *
 * @param name user-supplied column header (validated, never null, never blank)
 * @param type optional formatting token (reserved; not interpreted by V1)
 */
public record SpreadsheetColumn(String name, String type) {

    public SpreadsheetColumn {
        Objects.requireNonNull(name, "column name 不能为空");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("column name 不能为空");
        }
        if (type != null) {
            type = type.strip();
            if (type.isEmpty()) {
                type = null;
            }
        }
    }

    public static SpreadsheetColumn of(String name) {
        return new SpreadsheetColumn(name, null);
    }
}
