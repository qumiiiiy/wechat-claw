package com.weida.bot.wechatilinkbot.agent;

import java.io.IOException;

/**
 * Provider-neutral port for one model completion.
 *
 * <p>The agent owns the function-calling loop; implementations perform exactly one remote
 * model request and never execute tools.</p>
 */
public interface AgentModelClient {

    boolean available();

    /** Stable, non-secret identifier for audit records produced by this model client. */
    default String modelIdentifier() {
        return "agent-model";
    }

    ModelResponse complete(ModelRequest request) throws IOException;
}
