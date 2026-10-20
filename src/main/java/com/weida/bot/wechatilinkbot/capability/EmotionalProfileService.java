package com.weida.bot.wechatilinkbot.capability;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Long-term emotional-profile memory that survives beyond the normal
 * conversation-history window so the agent remembers key facts about each
 * user across sessions.
 *
 * <p>Profile updates are free: the system prompt instructs the LLM to append
 * {@code [EMOTION: …] [CONTEXT: …]} markers at the end of each reply, and
 * this service strips them before the user sees the text.</p>
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class EmotionalProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(EmotionalProfileService.class);

    private static final Pattern PROFILE_MARKER_PATTERN = Pattern.compile(
            "\\s*\\[EMOTION:\\s*([^]]*)]\\s*"
                    + "(?:\\[MOOD:\\s*(low|neutral|positive)])?\\s*"
                    + "\\[CONTEXT:\\s*([^]]*)]\\s*$");
    private static final Pattern SINGLE_MARKER_PATTERN = Pattern.compile(
            "\\s*\\[(?:EMOTION|CONTEXT):\\s*([^]]*)]\\s*"
                    + "(?:\\[MOOD:\\s*(low|neutral|positive)])?\\s*$");

    private final ConversationProfileProperties properties;
    private final Map<String, ProfileEntry> profileMap = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemDefaultZone();

    public EmotionalProfileService(ConversationProfileProperties properties) {
        this.properties = properties;
    }

    /**
     * Gets the accumulated emotional profile text for one conversation.
     *
     * @param conversationId stable channel-scoped conversation identifier
     * @return profile text ready to inject into the system prompt, or empty
     */
    public String getProfile(String conversationId) {
        if (!properties.isEnabled() || conversationId == null) {
            return "";
        }
        ProfileEntry entry = profileMap.get(conversationId);
        if (entry == null) {
            return "";
        }
        if (entry.expiresAt.isBefore(clock.instant())) {
            profileMap.remove(conversationId, entry);
            return "";
        }
        return entry.profileText;
    }

    /**
     * Reports whether emotional-profile tracking is enabled.
     *
     * @return {@code true} when profile memory is active
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Returns a snapshot of all active profile entries keyed by conversation
     * identifier. Used by scheduled components that need to iterate profiles.
     *
     * @return unmodifiable snapshot of the current profile map
     */
    public Map<String, String> getProfileSnapshot() {
        Instant now = clock.instant();
        Map<String, String> snapshot = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ProfileEntry> entry : profileMap.entrySet()) {
            if (entry.getValue().expiresAt.isAfter(now)) {
                snapshot.put(entry.getKey(), entry.getValue().profileText);
            }
        }
        return snapshot;
    }

    /**
     * Extracts emotional-profile markers from the agent's reply text and
     * persists a merged profile for the given conversation.
     *
     * <p>Returns the user-visible text with markers removed. If no markers are
     * found the original text is returned unchanged.</p>
     *
     * @param conversationId stable channel-scoped conversation identifier
     * @param agentReply     the full agent reply, possibly ending with markers
     * @return user-visible text with markers stripped
     */
    public String extractAndUpdateProfile(String conversationId, String agentReply) {
        if (!properties.isEnabled() || conversationId == null || agentReply == null) {
            return agentReply == null ? "" : agentReply;
        }
        String cleaned = agentReply;
        String emotion = "";
        String mood = "";
        String context = "";
        Matcher fullMatcher = PROFILE_MARKER_PATTERN.matcher(cleaned);
        if (fullMatcher.find()) {
            emotion = fullMatcher.group(1).strip();
            mood = fullMatcher.group(2) == null ? "" : fullMatcher.group(2).strip();
            context = fullMatcher.group(3).strip();
            cleaned = fullMatcher.replaceFirst("");
        } else {
            Matcher singleMatcher = SINGLE_MARKER_PATTERN.matcher(cleaned);
            if (singleMatcher.find()) {
                cleaned = singleMatcher.replaceFirst("");
            }
        }
        if (!emotion.isEmpty() || !mood.isEmpty() || !context.isEmpty()) {
            persistProfile(conversationId, emotion, mood, context);
        }
        return cleaned;
    }

    private void persistProfile(
            String conversationId,
            String emotion,
            String mood,
            String context) {
        if (profileMap.size() >= properties.getMaxProfiles()
                && !profileMap.containsKey(conversationId)) {
            evictExpired();
        }
        Instant now = clock.instant();
        String newEntry = formatEntry(emotion, mood, context);
        profileMap.merge(
                conversationId,
                new ProfileEntry(newEntry, now.plus(properties.getTtl())),
                (existing, incoming) -> {
                    String merged = mergeProfiles(existing.profileText, incoming.profileText);
                    return new ProfileEntry(
                            truncated(merged),
                            now.plus(properties.getTtl()));
                });


        LOG.info(
                "emotional_profile_updated conversation={} emotion={} mood={} context={}",
                conversationReference(conversationId),
                truncated(emotion, 60),
                mood.isBlank() ? "unknown" : mood,
                truncated(context, 60));
    }

    private String formatEntry(String emotion, String mood, String context) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户最近状态：");
        if (!emotion.isEmpty()) {
            builder.append(emotion);
        }
        if (!context.isEmpty()) {
            if (!emotion.isEmpty()) {
                builder.append("。");
            }
            builder.append("上次提及：").append(context);
        }
        if (!mood.isEmpty()) {
            builder.append("\n[MOOD: ").append(mood).append("]");
        }
        return truncated(builder.toString());
    }

    private String mergeProfiles(String existing, String incoming) {
        String base = existing;

        if (base.length() > properties.getMaxProfileCharacters() / 2) {
            int firstNewline = base.indexOf('\n');
            if (firstNewline > 0) {
                base = base.substring(0, firstNewline);
            }
        }
        if (!base.isEmpty()) {
            base += "\n";
        }
        base += incoming;
        return truncated(base);
    }

    private String truncated(String value) {
        if (value.length() <= properties.getMaxProfileCharacters()) {
            return value;
        }
        return value.substring(0, properties.getMaxProfileCharacters()) + "…";
    }

    private void evictExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, ProfileEntry>> iterator = profileMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ProfileEntry> entry = iterator.next();
            if (entry.getValue().expiresAt.isBefore(now)) {
                iterator.remove();
            }
        }
        if (profileMap.size() >= properties.getMaxProfiles()) {
            Map.Entry<String, ProfileEntry> oldest = null;
            for (Map.Entry<String, ProfileEntry> entry : profileMap.entrySet()) {
                if (oldest == null
                        || entry.getValue().expiresAt.isBefore(oldest.getValue().expiresAt)) {
                    oldest = entry;
                }
            }
            if (oldest != null) {
                profileMap.remove(oldest.getKey());
            }
        }
    }

    private static String conversationReference(String conversationId) {
        if (conversationId == null) {
            return "unknown";
        }
        int prefixIndex = conversationId.indexOf('|');
        String userId = prefixIndex >= 0
                ? conversationId.substring(prefixIndex + 1)
                : conversationId;
        return maskIdentifier(userId);
    }

    private static String maskIdentifier(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private static String truncated(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private static final class ProfileEntry {

        final String profileText;
        final Instant expiresAt;

        ProfileEntry(String profileText, Instant expiresAt) {
            this.profileText = profileText;
            this.expiresAt = expiresAt;
        }
    }
}
