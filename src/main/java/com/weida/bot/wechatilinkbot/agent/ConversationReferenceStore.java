package com.weida.bot.wechatilinkbot.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Keeps short-lived, conversation-scoped aliases for objects returned by tools.
 *
 * <p>The store is intentionally not persistence and never stores message tokens, media bytes,
 * or user prompts. It only bridges a natural follow-up such as “第一个” to an opaque identifier
 * that the owning capability will validate again.</p>
 */
@Component
public class ConversationReferenceStore {

    private static final Pattern NUMERIC_ORDINAL = Pattern.compile(
            "(?:第\\s*)?(\\d+)(?:个|项|条|号|门|份|张|篇)?");
    private static final Pattern CHINESE_ORDINAL = Pattern.compile(
            "第([一二三四五六七八九十百])(?:个|项|条|号|门|份|张|篇)?");
    private static final Pattern LATEST_PATTERN = Pattern.compile(
            "刚才|刚刚|最近|上一个|上一项|当前|这个|这份|这门|那个|那份|之前|前一个|"
                    + "进度|状态|怎么样|查询|查看|确认|同意|执行|取消|撤销|终止|重试|重来");
    private static final int MAX_IDENTIFIER_CHARACTERS = 256;
    private static final int MAX_LABEL_CHARACTERS = 512;

    private final Duration expireAfter;
    private final int maxReferences;
    private final Clock clock;
    private final Map<String, List<Reference>> referencesByConversation = new LinkedHashMap<>();
    private long nextGroupSequence;

    @Autowired
    public ConversationReferenceStore(AgentProperties properties) {
        this(properties.getReferenceExpireAfter(), properties.getMaxConversationReferences(), Clock.systemUTC());
    }

    ConversationReferenceStore(Duration expireAfter, int maxReferences, Clock clock) {
        if (expireAfter == null || expireAfter.isZero() || expireAfter.isNegative()) {
            throw new IllegalArgumentException("引用有效期必须大于 0");
        }
        if (maxReferences < 1) {
            throw new IllegalArgumentException("引用数量上限必须大于 0");
        }
        this.expireAfter = expireAfter;
        this.maxReferences = maxReferences;
        this.clock = clock;
    }

    /** Stores one ordered result set for one conversation and one object type. */
    public synchronized void rememberGroup(
            String conversationId,
            ReferenceType type,
            List<ReferenceDraft> drafts) {
        String conversation = requireConversation(conversationId);
        if (type == null || drafts == null || drafts.isEmpty()) {
            return;
        }
        pruneExpired();
        long groupSequence = ++nextGroupSequence;
        Instant createdAt = clock.instant();
        List<Reference> conversationReferences = referencesByConversation.computeIfAbsent(
                conversation,
                ignored -> new ArrayList<>());
        for (int index = 0; index < drafts.size(); index++) {
            ReferenceDraft draft = drafts.get(index);
            if (draft == null) {
                continue;
            }
            conversationReferences.add(new Reference(
                    type,
                    requireIdentifier(draft.identifier()),
                    requireLabel(draft.label()),
                    index + 1,
                    groupSequence,
                    createdAt,
                    createdAt.plus(expireAfter),
                    safeAttributes(draft.attributes())));
        }
        trim(conversationReferences);
    }

    /** Returns the object selected by a natural ordinal, recency phrase, or exact internal ID. */
    public synchronized Optional<Reference> resolve(
            String conversationId,
            ReferenceType type,
            String userText) {
        String conversation = normalizeConversation(conversationId);
        if (conversation.isBlank() || type == null || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        pruneExpired();
        List<Reference> candidates = referencesByConversation.getOrDefault(
                conversation,
                List.of()).stream()
                .filter(reference -> reference.type() == type)
                .sorted(Comparator.comparingLong(Reference::groupSequence).reversed())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String normalizedText = userText.strip().toLowerCase(Locale.ROOT);
        Optional<Reference> exact = candidates.stream()
                .filter(reference -> normalizedText.contains(reference.identifier().toLowerCase(Locale.ROOT)))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        int ordinal = ordinal(normalizedText);
        if (ordinal > 0) {
            long latestGroup = candidates.stream()
                    .mapToLong(Reference::groupSequence)
                    .filter(sequence -> candidates.stream().anyMatch(
                            reference -> reference.groupSequence() == sequence
                                    && reference.ordinal() == ordinal))
                    .max()
                    .orElse(-1L);
            if (latestGroup >= 0) {
                return candidates.stream()
                        .filter(reference -> reference.groupSequence() == latestGroup
                                && reference.ordinal() == ordinal)
                        .findFirst();
            }
        }

        if (LATEST_PATTERN.matcher(normalizedText).find()) {
            return candidates.stream().max(Comparator.comparingLong(Reference::groupSequence));
        }
        return Optional.empty();
    }

    /** Adds a private, bounded hint for tool selection without changing the user's text/history. */
    public synchronized String modelHint(String conversationId, String userText, ReferenceType type) {
        return resolve(conversationId, type, userText)
                .map(reference -> "[服务端已解析的可信引用，仅供工具参数使用；不要向用户输出内部编号] "
                        + type.name().toLowerCase(Locale.ROOT)
                        + "=" + reference.identifier()
                        + "；名称=" + reference.label())
                .orElse("");
    }

    public synchronized boolean hasActiveReference(String conversationId, ReferenceType type) {
        String conversation = normalizeConversation(conversationId);
        if (conversation.isBlank() || type == null) {
            return false;
        }
        pruneExpired();
        return referencesByConversation.getOrDefault(conversation, List.of()).stream()
                .anyMatch(reference -> reference.type() == type);
    }

    public synchronized Optional<String> labelFor(String conversationId, ReferenceType type, String identifier) {
        String conversation = normalizeConversation(conversationId);
        if (conversation.isBlank() || type == null || identifier == null) {
            return Optional.empty();
        }
        pruneExpired();
        return referencesByConversation.getOrDefault(conversation, List.of()).stream()
                .filter(reference -> reference.type() == type)
                .filter(reference -> reference.identifier().equals(identifier.strip()))
                .max(Comparator.comparingLong(Reference::groupSequence))
                .map(Reference::label);
    }

    public synchronized void clear(String conversationId) {
        String conversation = normalizeConversation(conversationId);
        if (!conversation.isBlank()) {
            referencesByConversation.remove(conversation);
        }
    }

    public synchronized int size(String conversationId) {
        String conversation = normalizeConversation(conversationId);
        pruneExpired();
        return referencesByConversation.getOrDefault(conversation, List.of()).size();
    }

    private void trim(List<Reference> references) {
        while (references.size() > maxReferences) {
            references.remove(0);
        }
    }

    private void pruneExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, List<Reference>>> conversations =
                referencesByConversation.entrySet().iterator();
        while (conversations.hasNext()) {
            Map.Entry<String, List<Reference>> conversation = conversations.next();
            List<Reference> references = conversation.getValue().stream()
                    .filter(reference -> reference.expiresAt().isAfter(now))
                    .toList();
            if (references.isEmpty()) {
                conversations.remove();
            } else {
                conversation.setValue(new ArrayList<>(references));
            }
        }
    }

    private static int ordinal(String text) {
        Matcher numeric = NUMERIC_ORDINAL.matcher(text);
        while (numeric.find()) {
            try {
                return Integer.parseInt(numeric.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        Matcher chinese = CHINESE_ORDINAL.matcher(text);
        if (!chinese.find()) {
            return -1;
        }
        return switch (chinese.group(1)) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            case "百" -> 100;
            default -> -1;
        };
    }

    private static Map<String, String> safeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value.length() <= MAX_LABEL_CHARACTERS) {
                copy.put(key.strip(), value.strip());
            }
        });
        return Map.copyOf(copy);
    }

    private static String requireConversation(String value) {
        String conversation = normalizeConversation(value);
        if (conversation.isBlank()) {
            throw new IllegalArgumentException("引用缺少可信会话身份");
        }
        return conversation;
    }

    private static String normalizeConversation(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDENTIFIER_CHARACTERS) {
            return "";
        }
        return value.strip();
    }

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_IDENTIFIER_CHARACTERS) {
            throw new IllegalArgumentException("引用编号无效");
        }
        return value.strip();
    }

    private static String requireLabel(String value) {
        if (value == null || value.isBlank()) {
            return "未命名对象";
        }
        String label = value.strip();
        return label.length() > MAX_LABEL_CHARACTERS
                ? label.substring(0, MAX_LABEL_CHARACTERS)
                : label;
    }

    public enum ReferenceType {
        FILE,
        GENERATED_FILE,
        TODO,
        DESKTOP_TASK,
        OCS_SESSION,
        COURSE
    }

    public record ReferenceDraft(
            String identifier,
            String label,
            Map<String, String> attributes) {
    }

    public record Reference(
            ReferenceType type,
            String identifier,
            String label,
            int ordinal,
            long groupSequence,
            Instant createdAt,
            Instant expiresAt,
            Map<String, String> attributes) {
    }
}
