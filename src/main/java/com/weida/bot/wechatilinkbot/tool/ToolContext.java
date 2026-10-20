package com.weida.bot.wechatilinkbot.tool;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import java.util.List;

/**
 * Trusted runtime context supplied outside model-controlled arguments.
 *
 * @param conversationId stable conversation identity
 * @param currentUserText current inbound user text
 * @param emailSendRequested whether this turn belongs to the email workflow
 * @param weatherInformationRequested whether email content requests weather data
 * @param weatherInformationResolved whether a weather tool already succeeded in this run
 * @param emailAttachmentRequested whether the user asked to attach a generated file
 * @param availableGeneratedFiles server-owned files eligible for attachment
 * @param recentInboundFileNames recent trusted conversation file names available for file lookup
 */
public record ToolContext(
        String conversationId,
        String currentUserText,
        boolean emailSendRequested,
        boolean weatherInformationRequested,
        boolean weatherInformationResolved,
        boolean emailAttachmentRequested,
        List<GeneratedMedia> availableGeneratedFiles,
        List<String> recentInboundFileNames) {

    public ToolContext {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        if (currentUserText == null || currentUserText.isBlank()) {
            throw new IllegalArgumentException("currentUserText 不能为空");
        }
        conversationId = conversationId.strip();
        currentUserText = currentUserText.strip();
        availableGeneratedFiles = availableGeneratedFiles == null
                ? List.of()
                : List.copyOf(availableGeneratedFiles);
        recentInboundFileNames = recentInboundFileNames == null
                ? List.of()
                : recentInboundFileNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::strip)
                        .distinct()
                        .limit(8)
                        .toList();
    }

    public ToolContext(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            boolean weatherInformationRequested,
            boolean weatherInformationResolved,
            boolean emailAttachmentRequested,
            List<GeneratedMedia> availableGeneratedFiles) {
        this(
                conversationId,
                currentUserText,
                emailSendRequested,
                weatherInformationRequested,
                weatherInformationResolved,
                emailAttachmentRequested,
                availableGeneratedFiles,
                List.of());
    }

    public ToolContext(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            boolean weatherInformationRequested,
            boolean weatherInformationResolved) {
        this(
                conversationId,
                currentUserText,
                emailSendRequested,
                weatherInformationRequested,
                weatherInformationResolved,
                false,
                List.of());
    }

    public ToolContext(String conversationId, String currentUserText) {
        this(conversationId, currentUserText, false, false, false);
    }
}
