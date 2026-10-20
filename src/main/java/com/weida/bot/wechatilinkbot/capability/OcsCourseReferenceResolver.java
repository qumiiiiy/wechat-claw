package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Stores and resolves Bot-local C-numbers without exposing them as platform identifiers. */
@Component
public class OcsCourseReferenceResolver {

    private static final Pattern COURSE_REFERENCE_PATTERN = Pattern.compile("C[1-9][0-9]{0,2}");
    private static final Pattern NUMERIC_IDENTIFIER_PATTERN = Pattern.compile("[1-9][0-9]{0,19}");
    private static final Pattern NAME_SEPARATOR_PATTERN = Pattern.compile("[\\s\\p{P}\\p{S}]+");
    private static final Pattern ENC_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String TARGET_COURSE_URL =
            "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse";
    private static final int MAX_CONVERSATIONS = 100;
    private static final int MAX_CONVERSATION_CHARACTERS = 200;

    private final Map<String, CourseSnapshot> targetMap = new LinkedHashMap<>();

    public synchronized void remember(String conversationId, List<CourseDetail> details) {
        remember(conversationId, details, null, "");
    }

    public synchronized void remember(
            String conversationId,
            List<CourseDetail> details,
            CourseDetail selectedDetail) {
        remember(conversationId, details, selectedDetail, "");
    }

    public synchronized void remember(
            String conversationId,
            List<CourseDetail> details,
            CourseDetail selectedDetail,
            String fallbackEnc) {
        String conversation = normalizeConversation(conversationId);
        if (conversation.isBlank() || details == null) {
            return;
        }
        if (!targetMap.containsKey(conversation) && targetMap.size() >= MAX_CONVERSATIONS) {
            targetMap.remove(targetMap.keySet().iterator().next());
        }
        String safeFallbackEnc = normalizeEnc(fallbackEnc);
        List<CourseTarget> targets = details.stream()
                .map(detail -> CourseTarget.from(detail, safeFallbackEnc))
                .toList();
        int selectedIndex = selectedDetail == null ? -1 : details.indexOf(selectedDetail);
        String selectedReference = selectedIndex >= 0 && selectedIndex < targets.size()
                ? "C" + (selectedIndex + 1)
                : "";
        targetMap.put(conversation, new CourseSnapshot(targets, selectedReference));
    }

    public synchronized URI resolve(
            String conversationId,
            String courseReference,
            String expectedCourseName) {
        String conversation = requireConversation(conversationId);
        String reference = normalizeReference(courseReference);
        CourseSnapshot snapshot = targetMap.get(conversation);
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "课程引用已失效，请先重新发送“查课程”再启动 OCS");
        }
        int index = Integer.parseInt(reference.substring(1)) - 1;
        if (index >= snapshot.targets().size()) {
            throw new IllegalArgumentException("课程引用不存在，请重新发送“查课程”");
        }
        CourseTarget target = snapshot.targets().get(index);
        requireMatchingCourseName(target.courseName(), expectedCourseName);
        return target.toUri();
    }

    public synchronized Optional<ResolvedCourse> resolveLatestSelection(
            String conversationId,
            String expectedCourseName) {
        String conversation = requireConversation(conversationId);
        CourseSnapshot snapshot = targetMap.get(conversation);
        if (snapshot == null || snapshot.selectedReference().isBlank()) {
            return Optional.empty();
        }
        URI targetUrl = resolve(
                conversation,
                snapshot.selectedReference(),
                expectedCourseName);
        return Optional.of(new ResolvedCourse(snapshot.selectedReference(), targetUrl));
    }

    public synchronized void clear(String conversationId) {
        String conversation = normalizeConversation(conversationId);
        if (!conversation.isBlank()) {
            targetMap.remove(conversation);
        }
    }

    private static String normalizeReference(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!COURSE_REFERENCE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("课程引用必须是 C1 至 C999");
        }
        return normalized;
    }

    private static String requireConversation(String value) {
        String normalized = normalizeConversation(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("课程引用缺少可信会话身份");
        }
        return normalized;
    }

    private static String normalizeConversation(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CONVERSATION_CHARACTERS) {
            return "";
        }
        return value.strip();
    }

    private static void requireMatchingCourseName(String actual, String expected) {
        String normalizedActual = normalizeName(actual);
        String normalizedExpected = normalizeName(expected);
        if (normalizedActual.isBlank()
                || normalizedExpected.isBlank()
                || !normalizedActual.contains(normalizedExpected)
                        && !normalizedExpected.contains(normalizedActual)) {
            throw new IllegalArgumentException("课程引用与课程名称不一致，请重新发送“查课程”");
        }
    }

    private static String normalizeName(String value) {
        return value == null
                ? ""
                : NAME_SEPARATOR_PATTERN.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String requireIdentifier(String value, String fieldName) {
        String normalized = value == null ? "" : value.strip();
        if (!NUMERIC_IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " 缺失，无法打开课程");
        }
        return normalized;
    }

    private record CourseTarget(
            String courseName,
            String courseId,
            String classId,
            String cpi,
            String enc) {

        private static CourseTarget from(CourseDetail detail, String fallbackEnc) {
            String enc = normalizeEnc(detail.enc());
            if (enc.isBlank()) {
                enc = fallbackEnc;
            }
            return new CourseTarget(
                    detail.courseName(),
                    detail.courseId(),
                    detail.classId(),
                    detail.cpi(),
                    enc);
        }

        private URI toUri() {
            String url = TARGET_COURSE_URL
                    + "?courseid=" + requireIdentifier(courseId, "课程 ID")
                    + "&clazzid=" + requireIdentifier(classId, "班级 ID")
                    + "&cpi=" + requireIdentifier(cpi, "课程 cpi");
            if (!enc.isBlank()) {
                url += "&enc=" + enc;
            }
            url += "&ut=s";
            return URI.create(url);
        }
    }

    private static String normalizeEnc(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || ENC_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        return "";
    }

    private record CourseSnapshot(
            List<CourseTarget> targets,
            String selectedReference) {
    }

    public record ResolvedCourse(String courseReference, URI targetUrl) {
    }
}
