package com.weida.bot.wechatilinkbot.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.MediaKind;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-owned mutable state for one serialized function-calling loop.
 */
public final class ToolRunContext {

    private static final String WEATHER_TOOL_NAME = "get_current_weather";

    private final String conversationId;
    private final String currentUserText;
    private final boolean emailSendRequested;
    private final boolean weatherInformationRequested;
    private final boolean emailAttachmentRequested;
    private final List<String> recentInboundFileNameList = new ArrayList<>();
    private final int mediaLimit;
    private final ObjectMapper objectMapper;
    private final Set<String> attemptedToolNameSet = new LinkedHashSet<>();
    private final Set<String> successfulToolNameSet = new LinkedHashSet<>();
    private final Map<String, ToolExecution> executionMap = new LinkedHashMap<>();
    private final List<GeneratedMedia> generatedMediaList = new ArrayList<>();
    private final List<GeneratedMedia> availableGeneratedFileList = new ArrayList<>();
    private boolean weatherInformationResolved;

    public ToolRunContext(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            boolean weatherInformationRequested,
            int mediaLimit,
            ObjectMapper objectMapper) {
        this(
                conversationId,
                currentUserText,
                emailSendRequested,
                weatherInformationRequested,
                false,
                List.of(),
                List.of(),
                mediaLimit,
                objectMapper);
    }

    public ToolRunContext(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            boolean weatherInformationRequested,
            boolean emailAttachmentRequested,
            List<GeneratedMedia> availableGeneratedFiles,
            int mediaLimit,
            ObjectMapper objectMapper) {
        this(
                conversationId,
                currentUserText,
                emailSendRequested,
                weatherInformationRequested,
                emailAttachmentRequested,
                availableGeneratedFiles,
                List.of(),
                mediaLimit,
                objectMapper);
    }

    public ToolRunContext(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            boolean weatherInformationRequested,
            boolean emailAttachmentRequested,
            List<GeneratedMedia> availableGeneratedFiles,
            List<String> recentInboundFileNames,
            int mediaLimit,
            ObjectMapper objectMapper) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        if (currentUserText == null || currentUserText.isBlank()) {
            throw new IllegalArgumentException("currentUserText 不能为空");
        }
        if (mediaLimit < 0) {
            throw new IllegalArgumentException("mediaLimit 不能小于 0");
        }
        this.conversationId = conversationId.strip();
        this.currentUserText = currentUserText.strip();
        this.emailSendRequested = emailSendRequested;
        this.weatherInformationRequested = weatherInformationRequested;
        this.emailAttachmentRequested = emailAttachmentRequested;
        this.mediaLimit = mediaLimit;
        this.objectMapper = objectMapper;
        if (availableGeneratedFiles != null) {
            availableGeneratedFiles.stream()
                    .filter(media -> media != null && media.kind() == GeneratedMediaKind.FILE)
                    .forEach(availableGeneratedFileList::add);
        }
        if (recentInboundFileNames != null) {
            recentInboundFileNames.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::strip)
                    .distinct()
                    .limit(8)
                    .forEach(recentInboundFileNameList::add);
        }
    }

    public synchronized boolean mediaAllowed() {
        return generatedMediaList.size() < mediaLimit;
    }

    public synchronized ToolContext trustedContext() {
        return new ToolContext(
                conversationId,
                currentUserText,
                emailSendRequested,
                weatherInformationRequested,
                weatherInformationResolved,
                emailAttachmentRequested,
                availableGeneratedFileList(),
                recentInboundFileNameList());
    }

    public synchronized void record(String toolName, ToolExecution execution) {
        record(toolName, execution, true);
    }

    /** Records the latest execution together with the registry's normalized outcome. */
    public synchronized void record(
            String toolName,
            ToolExecution execution,
            boolean succeeded) {
        attemptedToolNameSet.add(toolName);
        executionMap.put(toolName, execution);
        if (succeeded) {
            successfulToolNameSet.add(toolName);
        } else {
            successfulToolNameSet.remove(toolName);
        }
        if (execution.generatedMedia() != null) {
            generatedMediaList.add(execution.generatedMedia());
            if (execution.generatedMedia().kind() == GeneratedMediaKind.FILE) {
                availableGeneratedFileList.clear();
                availableGeneratedFileList.add(execution.generatedMedia());
            }
        }
        if (WEATHER_TOOL_NAME.equals(toolName) && succeeded(execution.content())) {
            weatherInformationResolved = true;
        }
    }

    public synchronized Optional<ToolExecution> latestExecution(String toolName) {
        return Optional.ofNullable(executionMap.get(toolName));
    }

    /** Returns whether the latest invocation of a tool completed successfully. */
    public synchronized boolean toolSucceeded(String toolName) {
        return successfulToolNameSet.contains(toolName);
    }

    /**
     * Returns image output from a tool in the provider-neutral shape accepted by the model.
     * Files and speech media remain channel-only artifacts and are never copied into the
     * model context.
     */
    public synchronized List<MediaPart> latestImageForModel(
            String toolName,
            int maxBytes) {
        if (toolName == null || toolName.isBlank() || maxBytes < 1) {
            return List.of();
        }
        Optional<ToolExecution> execution = latestExecution(toolName);
        if (execution.isEmpty() || execution.orElseThrow().generatedMedia() == null) {
            return List.of();
        }
        GeneratedMedia media = execution.orElseThrow().generatedMedia();
        if (media.kind() != GeneratedMediaKind.IMAGE || media.bytes().length > maxBytes) {
            return List.of();
        }
        return List.of(new MediaPart(
                MediaKind.IMAGE,
                imageMimeType(media.fileName()),
                media.bytes()));
    }

    public synchronized int attemptedToolCount() {
        return attemptedToolNameSet.size();
    }

    public synchronized List<GeneratedMedia> generatedMedia() {
        return List.copyOf(generatedMediaList);
    }

    /**
     * Keeps the latest generated file available as an email attachment while preventing it
     * from being delivered as an additional channel reply. This is used when a trusted file
     * reference is selected solely for an email draft.
     */
    public synchronized void suppressLatestGeneratedMediaDelivery() {
        if (!generatedMediaList.isEmpty()) {
            generatedMediaList.remove(generatedMediaList.size() - 1);
        }
    }

    private List<GeneratedMedia> availableGeneratedFileList() {
        return List.copyOf(availableGeneratedFileList);
    }

    private List<String> recentInboundFileNameList() {
        return List.copyOf(recentInboundFileNameList);
    }

    private boolean succeeded(String content) {
        try {
            JsonNode result = objectMapper.readTree(content);
            return "success".equals(result.path("status").asText());
        } catch (Exception exception) {
            return false;
        }
    }

    private static String imageMimeType(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
