package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.CodeFileSnapshot;
import com.weida.bot.wechatilinkbot.model.DiffResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Generates unified diff patches from file snapshots.
 *
 * <p>Model cannot specify server absolute paths. The diff is always
 * generated from a trusted snapshot and must be shown to the user
 * before application.</p>
 */
@Component
public class UnifiedDiffGenerator {

    private final CodeWorkspaceProperties properties;

    public UnifiedDiffGenerator(CodeWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a unified diff between the original snapshot and new content.
     *
     * @param snapshot the original file snapshot
     * @param newLines the new file content as lines
     * @return diff result with bounded output
     * @throws IllegalArgumentException if the diff exceeds limits
     */
    public DiffResult generate(CodeFileSnapshot snapshot, List<String> newLines) {
        if (snapshot == null) {
            throw new IllegalArgumentException("文件快照不能为空");
        }
        if (newLines == null) {
            throw new IllegalArgumentException("新内容不能为空");
        }

        List<String> oldLines = snapshot.lines();
        List<String[]> diffLines = computeDiff(oldLines, newLines);

        // Build unified diff header
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(snapshot.relativePath()).append("\n");
        sb.append("+++ b/").append(snapshot.relativePath()).append("\n");

        // Group diff lines into hunks
        List<String> hunkLines = new ArrayList<>();
        int hunkCount = 0;
        boolean inHunk = false;
        int oldLineNum = 1;
        int newLineNum = 1;

        for (String[] entry : diffLines) {
            String type = entry[0];
            String content = entry[1];

            if (type.equals("equal")) {
                if (inHunk) {
                    hunkLines.add(" " + content);
                }
                oldLineNum++;
                newLineNum++;
            } else if (type.equals("delete")) {
                if (!inHunk) {
                    // Start new hunk
                    int contextBefore = getContextBefore(diffLines, diffLines.indexOf(entry));
                    oldLineNum -= contextBefore;
                    newLineNum -= contextBefore;
                    hunkLines.add("@@ -" + oldLineNum + "," + countEqualAfter(diffLines, diffLines.indexOf(entry), "delete") + " +" + newLineNum + "," + countEqualAfter(diffLines, diffLines.indexOf(entry), "delete") + " @@");
                    inHunk = true;
                    hunkCount++;
                }
                hunkLines.add("-" + content);
                oldLineNum++;
            } else if (type.equals("insert")) {
                if (!inHunk) {
                    int contextBefore = getContextBefore(diffLines, diffLines.indexOf(entry));
                    oldLineNum -= contextBefore;
                    newLineNum -= contextBefore;
                    hunkLines.add("@@ -" + oldLineNum + "," + countEqualAfter(diffLines, diffLines.indexOf(entry), "insert") + " +" + newLineNum + "," + countEqualAfter(diffLines, diffLines.indexOf(entry), "insert") + " @@");
                    inHunk = true;
                    hunkCount++;
                }
                hunkLines.add("+" + content);
                newLineNum++;
            }
        }

        String diff = sb.toString() + String.join("\n", hunkLines);
        boolean truncated = hunkLines.size() > properties.getMaxDiffLines();

        if (truncated) {
            diff = diff.substring(0, Math.min(diff.length(), properties.getMaxDiffLines() * 80));
            diff += "\n... (diff truncated)";
        }

        return new DiffResult(
                snapshot.relativePath(),
                snapshot.fileId(),
                diff,
                hunkCount,
                hunkLines.size(),
                truncated);
    }

    private List<String[]> computeDiff(List<String> oldLines, List<String> newLines) {
        // Simple LCS-based diff
        int m = oldLines.size();
        int n = newLines.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack to find diff
        List<String[]> result = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                result.add(0, new String[]{"equal", oldLines.get(i - 1)});
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, new String[]{"insert", newLines.get(j - 1)});
                j--;
            } else {
                result.add(0, new String[]{"delete", oldLines.get(i - 1)});
                i--;
            }
        }
        return result;
    }

    private int getContextBefore(List<String[]> diffLines, int index) {
        int count = 0;
        for (int i = index - 1; i >= 0 && count < 3; i--) {
            if (diffLines.get(i)[0].equals("equal")) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private int countEqualAfter(List<String[]> diffLines, int index, String type) {
        int count = 0;
        for (int i = index; i < diffLines.size(); i++) {
            if (diffLines.get(i)[0].equals("equal")) {
                count++;
            } else if (!diffLines.get(i)[0].equals(type)) {
                break;
            }
        }
        return count + 1;
    }
}
