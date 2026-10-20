package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.QuestionSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 题目去重器。
 * 基于稳定指纹在同一学习会话内去重，防止重复请求 AI 或重复执行答案动作。
 */
public final class QuestionDeduplicator {

    private QuestionDeduplicator() {
    }

    /**
     * 判断题目是否在当前会话中已见过。
     *
     * @param fingerprint       题目指纹
     * @param seenFingerprints  已见过的指纹集合（调用方维护）
     * @return true 如果是重复题目
     */
    public static boolean isDuplicate(String fingerprint, Set<String> seenFingerprints) {
        Objects.requireNonNull(seenFingerprints, "seenFingerprints must not be null");
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        return seenFingerprints.contains(fingerprint.strip());
    }

    /**
     * 从快照列表中去重，保留首次出现的条目。
     *
     * @param snapshots 原始快照列表
     * @return 去重后的快照列表
     */
    public static List<QuestionSnapshot> deduplicate(List<QuestionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }

        Map<String, QuestionSnapshot> seen = new LinkedHashMap<>();
        for (QuestionSnapshot snapshot : snapshots) {
            String fp = snapshot.fingerprint();
            if (fp != null && !fp.isBlank()) {
                seen.putIfAbsent(fp, snapshot);
            }
        }
        return List.copyOf(seen.values());
    }
}
