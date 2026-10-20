package com.weida.bot.wechatilinkbot.infrastructure.diagram;

/**
 * Stable provider-visible error codes returned by diagram rendering failures.
 *
 * <p>Codes are emitted to the model and to logs; they must not contain
 * untrusted input.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
public final class DiagramErrorCode {

    public static final String DIAGRAM_DISABLED = "diagram_disabled";
    public static final String DIAGRAM_EXECUTABLE_MISSING = "diagram_executable_missing";
    public static final String DIAGRAM_SOURCE_REQUIRED = "diagram_source_required";
    public static final String DIAGRAM_SOURCE_TOO_LONG = "diagram_source_too_long";
    public static final String DIAGRAM_INVALID_TYPE = "diagram_invalid_type";
    public static final String DIAGRAM_FORBIDDEN_DIRECTIVE = "diagram_forbidden_directive";
    public static final String DIAGRAM_TIMEOUT = "diagram_timeout";
    public static final String DIAGRAM_PROCESS_FAILED = "diagram_process_failed";
    public static final String DIAGRAM_OUTPUT_INVALID = "diagram_output_invalid";
    public static final String DIAGRAM_OUTPUT_TOO_LARGE = "diagram_output_too_large";

    private DiagramErrorCode() {
    }
}