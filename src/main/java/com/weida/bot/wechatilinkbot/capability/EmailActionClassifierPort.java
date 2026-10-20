package com.weida.bot.wechatilinkbot.capability;

import java.util.List;

/**
 * Model-backed port that decides whether email subjects require user action.
 *
 * <p>Semantic judgment lives behind this port and is implemented by the model adapter in
 * infrastructure. Capability consumers keep the deterministic {@link EmailActionClassifier}
 * as a zero-latency fallback when the model port is unavailable, disabled or fails.</p>
 */
public interface EmailActionClassifierPort {

    /** Reports whether model-backed classification can be attempted now. */
    boolean available();

    /**
     * Classifies each subject in input order.
     *
     * @param subjects bounded list of plain-text subjects
     * @return one decision per input subject, in the same order
     * @throws EmailClassificationUnavailableException when the model cannot classify safely
     */
    List<Decision> classify(List<String> subjects);

    /** Stable, non-sensitive decision for one subject. */
    record Decision(String subject, boolean actionable) {

        public Decision {
            subject = subject == null ? "" : subject.strip();
        }
    }
}
