package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-conversation memory of recently listed pending items, enabling
 * natural-language references like "完成第二项" or "取消第一个".
 *
 * <p>References are consumed on read — once resolved, the mapping is kept
 * until the next list/query call replaces it.</p>
 */
@Component
public class PendingItemReference {

    private static final Map<String, Integer> CN_NUMERALS = Map.ofEntries(
            Map.entry("一", 1), Map.entry("二", 2), Map.entry("三", 3),
            Map.entry("四", 4), Map.entry("五", 5), Map.entry("六", 6),
            Map.entry("七", 7), Map.entry("八", 8), Map.entry("九", 9),
            Map.entry("十", 10), Map.entry("两", 2));

    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastOperationTarget = new ConcurrentHashMap<>();

    /** Stores the current item list for one conversation. */
    public void remember(String conversationId, List<PendingItem> items) {
        Map<Integer, PendingItem> bySeq = new LinkedHashMap<>();
        for (PendingItem item : items) {
            bySeq.put(item.sequenceNumber(), item);
        }
        snapshots.put(conversationId, new Snapshot(bySeq));
    }

    /** Records the last item that was acted upon (create/complete/cancel). */
    public void markLastOperation(String conversationId, String internalId) {
        lastOperationTarget.put(conversationId, internalId);
    }

    /**
     * Resolves a natural reference to an internal ID.
     *
     * <p>Supported forms:</p>
     * <ul>
     *   <li>{@code 第2项}, {@code 第 2 项} — sequence number</li>
     *   <li>{@code 第一个}, {@code 第二项} — Chinese numerals</li>
     *   <li>{@code 刚才的}, {@code 刚才那个} — last operated</li>
     *   <li>{@code TD-a1b2c3d4e5f6} — raw ID passthrough</li>
     * </ul>
     *
     * @param conversationId 会话标识
     * @param reference      自然引用文本或原始 ID
     * @return 解析结果，包含内部 ID 和来源；解析失败时 error 非空
     */
    public Resolution resolve(String conversationId, String reference) {
        String ref = reference == null ? "" : reference.strip();
        if (ref.isBlank()) {
            return Resolution.error("请指定要操作的待办项，例如「完成第2项」。");
        }

        // Raw TD-xxx passthrough (backward compatibility)
        if (ref.matches("(?i)td-[0-9a-f]{12}")) {
            return new Resolution(ref, null);
        }

        // "刚才的" / "刚才那个"
        if (ref.contains("刚才")) {
            String id = lastOperationTarget.get(conversationId);
            if (id == null) {
                return Resolution.error("没有最近操作的待办。请使用「第2项」指定。");
            }
            return new Resolution(id, null);
        }

        // Parse sequence number: "第N项" or Chinese numeral "第一个"
        int seq = parseSequence(ref);
        if (seq < 1) {
            return Resolution.error("无法理解「" + ref + "」，请使用「第2项」「第一个」或「刚才的」。");
        }

        Snapshot snapshot = snapshots.get(conversationId);
        if (snapshot == null) {
            return Resolution.error("请先查询待办列表，再指定要操作的项。");
        }
        PendingItem item = snapshot.bySequence.get(seq);
        if (item == null) {
            int size = snapshot.bySequence.size();
            return Resolution.error("第" + seq + "项不存在。当前共" + size + "项，请重新查询。");
        }
        return new Resolution(item.internalId(), item.source());
    }

    /** Returns the source of a resolved item, or null if unknown. */
    public PendingItemSource sourceOf(String conversationId, String internalId) {
        Snapshot snapshot = snapshots.get(conversationId);
        if (snapshot == null) return null;
        for (PendingItem item : snapshot.bySequence.values()) {
            if (item.internalId().equals(internalId)) {
                return item.source();
            }
        }
        return null;
    }

    public void clearConversation(String conversationId) {
        snapshots.remove(conversationId);
        lastOperationTarget.remove(conversationId);
    }

    // ── parsing ──────────────────────────────────────────────────

    static int parseSequence(String ref) {
        // Pure numeric: "2" / " 2 "
        var m = java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*$").matcher(ref);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        // "第2项" / "第 2 项"
        m = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*项").matcher(ref);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        // "第N个" / "第N"
        m = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*个?").matcher(ref);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        // "第二个" / "第一项"
        m = java.util.regex.Pattern.compile("第([一二三四五六七八九十两]+)\\s*[个项]?").matcher(ref);
        if (m.find()) {
            Integer num = CN_NUMERALS.get(m.group(1));
            if (num != null) return num;
        }
        // "下一项" — resolve as the first one
        if (ref.contains("下") && ref.contains("项")) {
            return 1;
        }
        return -1;
    }

    // ── types ─────────────────────────────────────────────────────

    public record Resolution(String internalId, PendingItemSource source, String error) {
        public Resolution(String internalId, PendingItemSource source) {
            this(internalId, source, null);
        }

        public boolean isError() { return error != null; }

        static Resolution error(String message) {
            return new Resolution(null, null, message);
        }
    }

    private record Snapshot(Map<Integer, PendingItem> bySequence) {}
}
