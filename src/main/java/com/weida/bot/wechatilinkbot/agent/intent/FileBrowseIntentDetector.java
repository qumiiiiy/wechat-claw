package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit requests to browse or search local files.
 *
 * <p>Matches phrases like "D盘有什么文件", "给我看下exe文件", "搜索D盘的文件" etc.
 * Generation requests (e.g. "生成一个文件") are excluded by the caller via
 * {@code textFileRequested} guard, so this detector focuses on browse/search intent.</p>
 */
@Component
public class FileBrowseIntentDetector {

    private static final Pattern FILE_ID_PATTERN = Pattern.compile(
            "file-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECTION_ACTION_PATTERN = Pattern.compile(
            "(?:发送|选择|选中|发给我|把这个文件发|send|select)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NATURAL_SELECTION_PATTERN = Pattern.compile(
            "(?:发|发送|选择|选中|把).{0,16}(?:第[一二三四五六七八九十0-9]+(?:个|项|份|号)?"
                    + "|刚才(?:的)?(?:文件|那个)?|这个(?:文件)?|上一份)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISK_FILE_PATTERN = Pattern.compile(
            "(?i)(?:[a-z]盘|[a-z]:[\\\\/]|磁盘|本地磁盘|本地文件)"
                    + ".{0,30}(?:文件|目录|文件夹|exe|txt|pdf|docx|xls|xlsx|zip|rar)");

    private static final Pattern EXPLICIT_FILE_NAME_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\s“\\\"/\\\\])[^\\s“”\\\"/\\\\]{1,120}"
                    + "\\.[a-z0-9]{1,8}(?:$|[\\s”\\\"，,。；;])");

    private static final Pattern DISK_DIRECTORY_PATTERN = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/][^，,。；;\\r\\n]{1,120}"
                    + "|[a-z]盘[^，,。；;\\r\\n]{1,120}"
                    + "(?:里面|目录|文件夹|路径|下|中|里|/|\\\\))");
    private static final Pattern NATURAL_DIRECTORY_DESCRIPTION_PATTERN = Pattern.compile(
            "(?i)(?:在|从|到|去)?[a-z]盘"
                    + "(?:(?:里面|下面|其中|之中|中的|里的|目录|文件夹|路径|的|在|下|中|里)"
                    + "|[\\p{L}\\p{N}_./\\\\-])+");
    private static final Pattern DIRECTORY_LISTING_PATTERN = Pattern.compile(
            "(?is)(?:文件夹|目录|子目录).{0,20}(?:罗列|列出|列举|有哪些|有什么|查看|浏览|显示|展示|看看)"
                    + "|(?:罗列|列出|列举|查看|浏览|显示|展示|看看).{0,20}(?:文件夹|目录|子目录)"
                    + "|(?:[a-z]盘|[a-z]:[\\\\/]).{0,30}(?:文件夹|目录|子目录)(?:一下|$)");
    private static final Pattern DIRECTORY_LISTING_CONTINUATION_PATTERN = Pattern.compile(
            "(?i)(?:罗列|列一下|列出来|继续列|重新列|再列|显示一下|把它们列)");

    private static final Pattern SEARCH_FILE_PATTERN = Pattern.compile(
            "(?is)(?:搜索|查找|找一下|找|列出|列举|显示|展示|看下|看一下|看看|有没有|有哪些|啥|什么)"
                    + ".{0,30}(?:文件|目录|文件夹|exe|txt|pdf|docx|xls|xlsx|zip|rar)");

    private static final Pattern FILE_TYPE_PATTERN = Pattern.compile(
            "(?is)(?:[a-z]盘|磁盘|本地|电脑|服务器).{0,30}"
                    + "(?:exe|txt|pdf|docx?|xls|xlsx|zip|rar|bat|cmd|msi|dll)");

    /** Returns whether the text asks to browse or search local files. */
    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        return hasSpecificFileOrDirectory(normalized)
                || DISK_FILE_PATTERN.matcher(normalized).find()
                || DIRECTORY_LISTING_PATTERN.matcher(normalized).find()
                || SEARCH_FILE_PATTERN.matcher(normalized).find()
                || FILE_TYPE_PATTERN.matcher(normalized).find();
    }

    /**
     * Returns whether the request contains enough information for a server-directed search.
     * A bare filename and a directory path are deterministic queries; generic requests such as
     * "帮我找一个文件" still remain available for the model to clarify.
     */
    public boolean hasSpecificFileOrDirectory(String text) {
        String normalized = normalize(text);
        return !normalized.isBlank()
                && (EXPLICIT_FILE_NAME_PATTERN.matcher(normalized).find()
                        || DISK_DIRECTORY_PATTERN.matcher(normalized).find()
                        || NATURAL_DIRECTORY_DESCRIPTION_PATTERN.matcher(normalized).find());
    }

    /** Returns whether the text explicitly asks to list directories. */
    public boolean hasDirectoryListingIntent(String text) {
        return DIRECTORY_LISTING_PATTERN.matcher(normalize(text)).find();
    }

    /** Returns whether the text is a short continuation of a previous directory listing. */
    public boolean hasDirectoryListingContinuation(String text) {
        return DIRECTORY_LISTING_CONTINUATION_PATTERN.matcher(normalize(text)).find();
    }

    /** Returns whether the user explicitly selected one previously returned opaque ID. */
    public boolean hasSelectionIntent(String text) {
        String normalized = normalize(text);
        return FILE_ID_PATTERN.matcher(normalized).find()
                && SELECTION_ACTION_PATTERN.matcher(normalized).find()
                || NATURAL_SELECTION_PATTERN.matcher(normalized).find();
    }

    /** Extracts the opaque ID only; it never accepts a path. */
    public Optional<String> extractFileId(String text) {
        var matcher = FILE_ID_PATTERN.matcher(normalize(text));
        return matcher.find()
                ? Optional.of(matcher.group().toLowerCase(Locale.ROOT))
                : Optional.empty();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
