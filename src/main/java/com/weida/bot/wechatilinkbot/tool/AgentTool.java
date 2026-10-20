package com.weida.bot.wechatilinkbot.tool;

/**
 * Provider-neutral SPI for one model-callable capability.
 *
 * <p>Implementations contain business validation and delegate external I/O to capability
 * classes. AI SDK types are intentionally absent from this contract.</p>
 *
 * @param <I> strongly typed tool input
 */
public interface AgentTool<I> {

    /** Returns the unique provider-visible function name. */
    String name();

    /** Returns the provider-visible purpose and usage constraints. */
    String description();

    /** Returns the input record used for JSON deserialization and schema generation. */
    Class<I> inputType();

    /** Reports whether configuration and local dependencies allow execution. */
    boolean available();

    /** Returns a stable, non-sensitive diagnostic code when unavailable. */
    default String availabilityReason() {
        return available() ? "available" : "unavailable";
    }

    /**
     * Reports whether the tool is hidden from narrow server-directed catalogs.
     * The model-directed catalog may still expose an available tool; high-risk tools must
     * enforce their actual authorization and approval rules during execution.
     */
    default boolean requiresExplicitIntent() {
        return false;
    }

    /** Reports whether execution can create billable channel media. */
    boolean producesMedia();

    /** Reports whether the result should bypass a follow-up model call. */
    default boolean returnDirect() {
        return false;
    }

    /** Executes a validated invocation. */
    ToolExecution execute(I input, ToolContext context);

    /** Clears tool-owned transient state for one conversation. */
    default void clearConversation(String conversationId) {
    }
}
