package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import java.util.Set;

/**
 * Diagram rendering failure carrying a stable error code from
 * {@link DiagramErrorCode}.
 *
 * <p>The original cause is preserved when available; the user-facing message
 * never embeds raw untrusted input.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
public class DiagramException extends RuntimeException {

    private static final Set<String> ALLOWED_CODES = Set.of(
            DiagramErrorCode.DIAGRAM_DISABLED,
            DiagramErrorCode.DIAGRAM_EXECUTABLE_MISSING,
            DiagramErrorCode.DIAGRAM_SOURCE_REQUIRED,
            DiagramErrorCode.DIAGRAM_SOURCE_TOO_LONG,
            DiagramErrorCode.DIAGRAM_INVALID_TYPE,
            DiagramErrorCode.DIAGRAM_FORBIDDEN_DIRECTIVE,
            DiagramErrorCode.DIAGRAM_TIMEOUT,
            DiagramErrorCode.DIAGRAM_PROCESS_FAILED,
            DiagramErrorCode.DIAGRAM_OUTPUT_INVALID,
            DiagramErrorCode.DIAGRAM_OUTPUT_TOO_LARGE);

    private final String code;

    public DiagramException(String code, String message) {
        this(code, message, null);
    }

    public DiagramException(String code, String message, Throwable cause) {
        super(message, cause);
        if (code == null || !ALLOWED_CODES.contains(code)) {
            throw new IllegalArgumentException("不支持的 Diagram 错误码：" + code);
        }
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}