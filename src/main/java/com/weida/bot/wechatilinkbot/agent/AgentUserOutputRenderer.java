package com.weida.bot.wechatilinkbot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Converts bounded tool output into text suitable for a phone screen. */
@Component
public class AgentUserOutputRenderer {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.of("Asia/Shanghai"));
    private static final Pattern TODO_LINE = Pattern.compile(
            "(?m)^\\s*(\\d+)\\s*[.、)]?\\s*\\[?(TD-[0-9a-f]{12})\\]?\\s*([^\\r\\n]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TODO_ID = Pattern.compile(
            "TD-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESKTOP_ID = Pattern.compile(
            "DT-[0-9a-f-]{8,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCS_ID = Pattern.compile(
            "OS-[0-9a-f-]{8,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_ID = Pattern.compile(
            "file-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE_LINE = Pattern.compile(
            "(?m)^\\s*\\[C(\\d+)\\](?:【([^】]+)】|\\s*([^\\r\\n]+))");
    private static final Pattern INTERNAL_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]|\\\\\\\\)[^\\r\\n，。；：,;!?！？\\s]+"
                    + "|(?:^|[\\s：:])/(?:[^\\s/]+/){2,}[^\\s]+",
            Pattern.MULTILINE);

    private final ObjectMapper objectMapper;
    private final ConversationReferenceStore referenceStore;

    public AgentUserOutputRenderer(
            ObjectMapper objectMapper,
            ConversationReferenceStore referenceStore) {
        this.objectMapper = objectMapper;
        this.referenceStore = referenceStore;
    }

    /** Captures internal references before formatting and returns user-facing text only. */
    public String render(
            String conversationId,
            String rawContent,
            List<GeneratedMedia> generatedMedia) {
        String content = rawContent == null ? "" : rawContent.strip();
        captureGeneratedMediaReferences(conversationId, generatedMedia);
        String structured = renderStructured(conversationId, content);
        String rendered = structured == null ? renderText(conversationId, content) : structured;
        rendered = sanitizeDisplay(rendered);
        if (rendered.isBlank() && generatedMedia != null && !generatedMedia.isEmpty()) {
            rendered = mediaSummary(generatedMedia);
        } else if (hasGenericMediaReply(rendered) && hasFileMedia(generatedMedia)) {
            rendered = mediaSummary(generatedMedia);
        }
        return rendered.strip();
    }

    private void captureGeneratedMediaReferences(
            String conversationId,
            List<GeneratedMedia> generatedMedia) {
        if (generatedMedia == null || generatedMedia.isEmpty()) {
            return;
        }
        List<ConversationReferenceStore.ReferenceDraft> drafts = generatedMedia.stream()
                .filter(media -> media != null && media.kind() == GeneratedMediaKind.FILE)
                .map(media -> new ConversationReferenceStore.ReferenceDraft(
                        safeFileName(media.fileName()),
                        safeFileName(media.fileName()),
                        java.util.Map.of()))
                .toList();
        if (!drafts.isEmpty()) {
            referenceStore.rememberGroup(
                    conversationId,
                    ConversationReferenceStore.ReferenceType.GENERATED_FILE,
                    drafts);
        }
    }

    private String renderStructured(
            String conversationId,
            String content) {
        JsonNode root = parse(content);
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode results = root.path("results");
        if (results.isArray() && hasFileResults(results)) {
            return renderFileResults(conversationId, root, results);
        }
        JsonNode directories = root.path("directories");
        if (directories.isArray() && hasDirectoryResults(directories)) {
            return renderDirectoryResults(directories);
        }
        String status = root.path("status").asText("");
        if ("error".equalsIgnoreCase(status)) {
            return safeMessage(root.path("message").asText("操作没有完成，请稍后重试。"));
        }
        if ("sent".equalsIgnoreCase(status)) {
            return "邮件已发送。";
        }
        if ("approval_required".equalsIgnoreCase(status)
                || "information_required".equalsIgnoreCase(status)) {
            return safeMessage(root.path("message").asText("请补充或确认邮件信息。"));
        }
        if (root.hasNonNull("file_name") && "ok".equalsIgnoreCase(status)) {
            String name = safeFileName(root.path("file_name").asText("文件"));
            return "已生成《" + name + "》，会作为本次微信回复发送。";
        }
        if (root.hasNonNull("message")) {
            return safeMessage(root.path("message").asText());
        }
        return looksLikeToolJson(content) ? "操作已处理，请查看本次回复中的结果。" : null;
    }

    private String renderFileResults(
            String conversationId,
            JsonNode root,
            JsonNode results) {
        List<ConversationReferenceStore.ReferenceDraft> drafts = new ArrayList<>();
        List<FileLine> lines = new ArrayList<>();
        for (JsonNode result : results) {
            String identifier = result.path("file_id").asText("");
            String name = safeFileName(result.path("file_name").asText(""));
            if (identifier.isBlank() || name.isBlank()) {
                continue;
            }
            int index = result.path("index").asInt(lines.size() + 1);
            String directory = safeRelativeDirectory(result.path("directory").asText(""));
            String size = result.path("size_human").asText("");
            Instant modified = parseInstant(result.path("last_modified").asText(""));
            drafts.add(new ConversationReferenceStore.ReferenceDraft(
                    identifier,
                    name,
                    java.util.Map.of("directory", directory)));
            lines.add(new FileLine(index, name, directory, size, modified));
        }
        referenceStore.rememberGroup(
                conversationId,
                ConversationReferenceStore.ReferenceType.FILE,
                drafts);
        if (lines.isEmpty()) {
            return "没有找到匹配的文件。可以换个名称、文件类型或目录再试。";
        }
        StringBuilder result = new StringBuilder("找到 ").append(lines.size()).append(" 个文件：\n");
        for (FileLine line : lines) {
            result.append(line.index()).append(". 《").append(line.name()).append("》");
            if (!line.directory().isBlank()) {
                result.append("（").append(line.directory()).append("）");
            }
            if (!line.size().isBlank()) {
                result.append(" · ").append(line.size());
            }
            if (line.modified() != null) {
                result.append(" · 修改于 ").append(DISPLAY_TIME.format(line.modified()));
            }
            result.append('\n');
        }
        result.append(lines.size() == 1
                ? "你可以说“把这个文件发给我”。"
                : "你可以说“把第一个发给我”或“把第二个发到邮箱”。");
        return result.toString();
    }

    private String renderDirectoryResults(JsonNode directories) {
        List<String> lines = new ArrayList<>();
        for (JsonNode directory : directories) {
            String name = safeFileName(directory.path("directory_name").asText(""));
            String relativePath = safeRelativeDirectory(
                    directory.path("relative_path").asText(""));
            if (name.equals("文件") || relativePath.isBlank()) {
                continue;
            }
            StringBuilder line = new StringBuilder()
                    .append(directory.path("index").asInt(lines.size() + 1))
                    .append(". 《")
                    .append(name)
                    .append("》");
            if (!relativePath.equals(name)) {
                line.append("（").append(relativePath).append("）");
            }
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            return "没有找到匹配的文件夹。可以换个目录再试。";
        }
        return "找到 " + lines.size() + " 个文件夹：\n"
                + String.join("\n", lines)
                + "\n如果要继续查文件，可以直接说文件名或指定其中一个目录。";
    }

    private String renderText(String conversationId, String content) {
        captureTextReferences(conversationId, content);
        String rendered = content;
        rendered = removeTodoIdentifiers(rendered);
        rendered = replaceCourseMarkers(rendered);
        rendered = replaceKnownStatus(rendered);
        rendered = rendered.replaceAll("(?i)\\b(?:browse_files|select_file|send_email|generate_document|"
                + "generate_spreadsheet|start_ocs_study|get_ocs_study_status)\\b", "系统能力");
        return rendered;
    }

    private void captureTextReferences(String conversationId, String content) {
        captureTodos(conversationId, content);
        captureIdentifiers(conversationId, content, DESKTOP_ID,
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK, "电脑任务");
        captureIdentifiers(conversationId, content, OCS_ID,
                ConversationReferenceStore.ReferenceType.OCS_SESSION, "课程学习");
        captureCourses(conversationId, content);
    }

    private void captureTodos(String conversationId, String content) {
        Matcher matcher = TODO_LINE.matcher(content);
        List<ConversationReferenceStore.ReferenceDraft> drafts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String identifier = matcher.group(2).toUpperCase(Locale.ROOT);
            if (seen.add(identifier)) {
                drafts.add(new ConversationReferenceStore.ReferenceDraft(
                        identifier,
                        todoLabel(matcher.group(3)),
                        java.util.Map.of()));
            }
        }
        if (drafts.isEmpty()) {
            Matcher idMatcher = TODO_ID.matcher(content);
            while (idMatcher.find()) {
                String identifier = idMatcher.group().toUpperCase(Locale.ROOT);
                if (seen.add(identifier)) {
                    String tail = content.substring(idMatcher.end());
                    drafts.add(new ConversationReferenceStore.ReferenceDraft(
                            identifier,
                            todoLabel(tail),
                            java.util.Map.of()));
                }
            }
        }
        if (!drafts.isEmpty()) {
            referenceStore.rememberGroup(
                    conversationId,
                    ConversationReferenceStore.ReferenceType.TODO,
                    drafts);
        }
    }

    private void captureIdentifiers(
            String conversationId,
            String content,
            Pattern pattern,
            ConversationReferenceStore.ReferenceType type,
            String label) {
        Matcher matcher = pattern.matcher(content);
        List<ConversationReferenceStore.ReferenceDraft> drafts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String identifier = matcher.group().toUpperCase(Locale.ROOT);
            if (seen.add(identifier)) {
                drafts.add(new ConversationReferenceStore.ReferenceDraft(identifier, label, java.util.Map.of()));
            }
        }
        if (!drafts.isEmpty()) {
            referenceStore.rememberGroup(conversationId, type, drafts);
        }
    }

    private void captureCourses(String conversationId, String content) {
        Matcher matcher = COURSE_LINE.matcher(content);
        List<ConversationReferenceStore.ReferenceDraft> drafts = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
            name = name == null ? "课程" : name.strip();
            drafts.add(new ConversationReferenceStore.ReferenceDraft(
                    "C" + matcher.group(1),
                    name,
                    java.util.Map.of()));
        }
        if (!drafts.isEmpty()) {
            referenceStore.rememberGroup(
                    conversationId,
                    ConversationReferenceStore.ReferenceType.COURSE,
                    drafts);
        }
    }

    private String removeTodoIdentifiers(String content) {
        return content.replaceAll("(?i)\\s*\\[TD-[0-9a-f]{12}]", "")
                .replaceAll("(?i)TD-[0-9a-f]{12}", "刚才的待办");
    }

    private String replaceCourseMarkers(String content) {
        Matcher matcher = COURSE_LINE.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(matcher.group(1) + ". " + name.strip()));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replaceAll("(?i)\\bC([1-9][0-9]{0,2})\\b", "第$1门课");
    }

    private String replaceKnownStatus(String content) {
        return content
                .replace("WAITING_APPROVAL", "等待确认")
                .replace("RUNNING", "进行中")
                .replace("SUCCEEDED", "已完成")
                .replace("FAILED", "失败")
                .replace("CANCELLED", "已取消")
                .replace("TIMED_OUT", "已过期")
                .replace("CREATED", "已创建")
                .replace("LAUNCHING", "正在打开")
                .replace("WAITING_LOGIN", "等待登录")
                .replace("DISCOVERING", "正在确认课程页面")
                .replaceAll("(?i)OS-[0-9a-f-]{8,}", "当前学习")
                .replaceAll("(?i)DT-[0-9a-f-]{8,}", "当前电脑任务");
    }

    private String removeInternalDetails(String content) {
        return INTERNAL_PATH.matcher(content == null ? "" : content)
                .replaceAll("指定文件")
                .replaceAll("\\{\\s*\"(?:status|message|file_id|taskId|sessionId)\\b[^}]*}", "")
                .strip();
    }

    private String sanitizeDisplay(String content) {
        String sanitized = removeInternalDetails(content);
        sanitized = removeTodoIdentifiers(sanitized);
        sanitized = replaceCourseMarkers(sanitized);
        sanitized = replaceKnownStatus(sanitized);
        sanitized = FILE_ID.matcher(sanitized).replaceAll("刚才的文件");
        return sanitized.replaceAll(
                "(?i)\\b(?:browse_files|select_file|send_email|generate_document|"
                        + "generate_presentation|generate_spreadsheet|start_ocs_study|"
                        + "get_ocs_study_status)\\b",
                "系统能力");
    }

    private String mediaSummary(List<GeneratedMedia> generatedMedia) {
        List<String> files = generatedMedia.stream()
                .filter(media -> media != null && media.kind() == GeneratedMediaKind.FILE)
                .map(GeneratedMedia::fileName)
                .map(this::safeFileName)
                .toList();
        if (!files.isEmpty()) {
            return "已准备" + files.stream().map(name -> "《" + name + "》").reduce((a, b) -> a + "、" + b).orElse("文件")
                    + "，会作为本次微信回复发送。";
        }
        return "已按你的要求处理完成。";
    }

    private boolean hasGenericMediaReply(String content) {
        return content != null && (content.contains("已按你的要求生成完成")
                || content.contains("已按你的要求处理完成"));
    }

    private boolean hasFileMedia(List<GeneratedMedia> generatedMedia) {
        return generatedMedia != null && generatedMedia.stream()
                .anyMatch(media -> media != null && media.kind() == GeneratedMediaKind.FILE);
    }

    private boolean hasFileResults(JsonNode results) {
        for (JsonNode result : results) {
            if (result.hasNonNull("file_id") && result.hasNonNull("file_name")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectoryResults(JsonNode directories) {
        for (JsonNode directory : directories) {
            if (directory.hasNonNull("directory_name")
                    && directory.hasNonNull("relative_path")) {
                return true;
            }
        }
        return false;
    }

    private JsonNode parse(String content) {
        if (!looksLikeToolJson(content)) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean looksLikeToolJson(String content) {
        return content != null && content.startsWith("{") && content.endsWith("}");
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "操作没有完成，请稍后重试。";
        }
        return removeInternalDetails(message.strip());
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "文件";
        }
        String value = name.strip().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        value = slash >= 0 ? value.substring(slash + 1) : value;
        return value.contains("..") || value.isBlank() ? "文件" : value;
    }

    private String safeRelativeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "";
        }
        String value = directory.strip().replace('\\', '/');
        if (value.startsWith("/")
                || value.startsWith("//")
                || value.matches("[A-Za-z]:.*")
                || value.contains(":")
                || value.contains("..")) {
            return "";
        }
        return value;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String todoLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "待办事项";
        }
        String value = raw.strip()
                .replaceFirst("^[：:、，,\\s]+", "")
                .replaceFirst("\\s*[—-]\\s*截止.*$", "")
                .replaceFirst("[，,。].*截止.*$", "")
                .strip();
        return value.isBlank() ? "待办事项" : value;
    }

    private record FileLine(
            int index,
            String name,
            String directory,
            String size,
            Instant modified) {
    }
}
