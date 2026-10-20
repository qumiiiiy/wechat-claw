package com.weida.bot.wechatilinkbot.infrastructure.learning;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学习通域名和链接识别工具。
 * 负责域名判定、课程 ID 提取、课程名称匹配和 URL 规范化。
 */
public final class QuestionDomainRecognizer {

    private static final Set<String> CHAOXING_HOSTS = Set.of(
            "mooc1.chaoxing.com",
            "mooc2.chaoxing.com",
            "mooc2-ans.chaoxing.com",
            "chaoxing.com",
            "passport2.chaoxing.com",
            "i.mooc.chaoxing.com",
            "mooc.chaoxing.com");

    private static final Pattern COURSE_ID_PARAM = Pattern.compile("[?&]courseId=(\\d+)");
    private static final Pattern COURSE_ID_PATH = Pattern.compile("/course/(\\d+)");

    private QuestionDomainRecognizer() {
    }

    /**
     * 判断 URL 是否为学习通域名。
     */
    public static boolean isChaoxingUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.strip());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (CHAOXING_HOSTS.contains(lowerHost)) {
                return true;
            }
            for (String allowed : CHAOXING_HOSTS) {
                if (lowerHost.endsWith("." + allowed)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 从 URL 提取 courseId。
     * 优先从 query 参数提取，降级到路径提取。
     */
    public static Optional<String> extractCourseId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String stripped = url.strip();

        Matcher paramMatcher = COURSE_ID_PARAM.matcher(stripped);
        if (paramMatcher.find()) {
            String id = paramMatcher.group(1);
            if (id != null && !id.isBlank()) {
                return Optional.of(id);
            }
        }

        Matcher pathMatcher = COURSE_ID_PATH.matcher(stripped);
        if (pathMatcher.find()) {
            String id = pathMatcher.group(1);
            if (id != null && !id.isBlank()) {
                return Optional.of(id);
            }
        }

        return Optional.empty();
    }

    /**
     * 判断课程名称是否模糊匹配（包含关键词，忽略大小写和空白）。
     */
    public static boolean matchesCourseName(String courseName, String keyword) {
        if (courseName == null || keyword == null) {
            return false;
        }
        String normalizedName = courseName.strip().toLowerCase(Locale.ROOT);
        String normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        if (normalizedName.isEmpty() || normalizedKeyword.isEmpty()) {
            return false;
        }
        return normalizedName.contains(normalizedKeyword);
    }

    /**
     * 规范化页面来源 URL：去除 query 参数和 fragment。
     */
    public static String normalizePageSource(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.strip());
            URI normalized = URI.create(
                    new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null)
                            .toASCIIString());
            return normalized.toString();
        } catch (Exception exception) {
            int queryStart = url.indexOf('?');
            if (queryStart >= 0) {
                return url.substring(0, queryStart);
            }
            int fragmentStart = url.indexOf('#');
            if (fragmentStart >= 0) {
                return url.substring(0, fragmentStart);
            }
            return url.strip();
        }
    }
}
