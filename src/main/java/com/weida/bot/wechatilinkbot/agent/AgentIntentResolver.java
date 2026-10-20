package com.weida.bot.wechatilinkbot.agent;

import com.weida.bot.wechatilinkbot.agent.intent.DiagramIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.agent.intent.EmailIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.FileBrowseIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.TodoIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.TodoOperation;
import com.weida.bot.wechatilinkbot.agent.intent.ImageIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.ImageReferenceDetector;
import com.weida.bot.wechatilinkbot.agent.intent.LearningIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.OcsStudyOperation;
import com.weida.bot.wechatilinkbot.agent.intent.PresentationIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.QrCodeIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.SpeechIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetOperation;
import com.weida.bot.wechatilinkbot.agent.intent.TextFileIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.VoicePreferenceResolver;
import com.weida.bot.wechatilinkbot.agent.intent.WeatherIntentDetector;
import com.weida.bot.wechatilinkbot.capability.EmailCapability;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Single entry point for natural-language routing decisions.
 */
@Component
public class AgentIntentResolver {

    private final SpeechIntentDetector speechDetector;
    private final ImageIntentDetector imageDetector;
    private final TextFileIntentDetector textFileDetector;
    private final SpreadsheetIntentDetector spreadsheetDetector;
    private final EmailIntentDetector emailDetector;
    private final WeatherIntentDetector weatherDetector;
    private final ImageReferenceDetector imageReferenceDetector;
    private final VoicePreferenceResolver voicePreferenceResolver;
    private final QrCodeIntentDetector qrCodeDetector;
    private final DiagramIntentDetector diagramDetector;
    private final DesktopTaskIntentDetector desktopTaskDetector;
    private final FileBrowseIntentDetector fileBrowseDetector;
    private final TodoIntentDetector todoIntentDetector;
    private final LearningIntentDetector learningDetector;
    private final EmailCapability emailCapability;
    private final PresentationIntentDetector presentationDetector;
    private final ConversationReferenceStore referenceStore;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentIntentResolver(
            SpeechIntentDetector speechDetector,
            ImageIntentDetector imageDetector,
            TextFileIntentDetector textFileDetector,
            SpreadsheetIntentDetector spreadsheetDetector,
            EmailIntentDetector emailDetector,
            WeatherIntentDetector weatherDetector,
            ImageReferenceDetector imageReferenceDetector,
            VoicePreferenceResolver voicePreferenceResolver,
            QrCodeIntentDetector qrCodeDetector,
            DiagramIntentDetector diagramDetector,
            DesktopTaskIntentDetector desktopTaskDetector,
            FileBrowseIntentDetector fileBrowseDetector,
            TodoIntentDetector todoIntentDetector,
            LearningIntentDetector learningDetector,
            EmailCapability emailCapability,
            PresentationIntentDetector presentationDetector,
            ConversationReferenceStore referenceStore) {
        this.speechDetector = speechDetector;
        this.imageDetector = imageDetector;
        this.textFileDetector = textFileDetector;
        this.spreadsheetDetector = spreadsheetDetector;
        this.emailDetector = emailDetector;
        this.weatherDetector = weatherDetector;
        this.imageReferenceDetector = imageReferenceDetector;
        this.voicePreferenceResolver = voicePreferenceResolver;
        this.qrCodeDetector = qrCodeDetector;
        this.diagramDetector = diagramDetector;
        this.desktopTaskDetector = desktopTaskDetector;
        this.fileBrowseDetector = fileBrowseDetector;
        this.todoIntentDetector = todoIntentDetector;
        this.learningDetector = learningDetector;
        this.emailCapability = emailCapability;
        this.presentationDetector = presentationDetector;
        this.referenceStore = referenceStore;
    }

    public AgentIntentResolver(
            SpeechIntentDetector speechDetector,
            ImageIntentDetector imageDetector,
            TextFileIntentDetector textFileDetector,
            SpreadsheetIntentDetector spreadsheetDetector,
            EmailIntentDetector emailDetector,
            WeatherIntentDetector weatherDetector,
            ImageReferenceDetector imageReferenceDetector,
            VoicePreferenceResolver voicePreferenceResolver,
            QrCodeIntentDetector qrCodeDetector,
            DiagramIntentDetector diagramDetector,
            DesktopTaskIntentDetector desktopTaskDetector,
            FileBrowseIntentDetector fileBrowseDetector,
            TodoIntentDetector todoIntentDetector,
            LearningIntentDetector learningDetector,
            EmailCapability emailCapability) {
        this(
                speechDetector,
                imageDetector,
                textFileDetector,
                spreadsheetDetector,
                emailDetector,
                weatherDetector,
                imageReferenceDetector,
                voicePreferenceResolver,
                qrCodeDetector,
                diagramDetector,
                desktopTaskDetector,
                fileBrowseDetector,
                todoIntentDetector,
                learningDetector,
                emailCapability,
                new PresentationIntentDetector(),
                new ConversationReferenceStore(
                        Duration.ofMinutes(30),
                        100,
                        Clock.systemUTC()));
    }

    public AgentIntent resolve(
            List<ConversationMessage> history,
            AgentRequest request) {
        String prompt = request.prompt();
        String currentUserText = request.currentUserText();
        VoicePreference voicePreference = voicePreferenceResolver.resolve(history, prompt);
        boolean presentationRequested = presentationDetector.hasIntent(currentUserText);
        boolean voiceSwitchRequested =
                voicePreferenceResolver.hasSwitchIntent(currentUserText);
        boolean explicitSpeechRequested = voiceSwitchRequested
                || speechDetector.hasIntent(currentUserText);
        SpreadsheetOperation spreadsheetOperation = spreadsheetDetector.detect(currentUserText);
        if (spreadsheetOperation == null) {
            spreadsheetOperation = spreadsheetDetector.hasIntent(currentUserText)
                    ? SpreadsheetOperation.GENERATE
                    : SpreadsheetOperation.NONE;
        }
        boolean spreadsheetRequested = spreadsheetOperation == SpreadsheetOperation.GENERATE;
        boolean textFileRequested = !spreadsheetRequested
                && !presentationRequested
                && textFileDetector.hasIntent(currentUserText);
        boolean documentRequested = textFileRequested
                && textFileDetector.hasDocumentIntent(currentUserText);
        boolean explicitEmailRequested = emailDetector.hasExplicitSendIntent(currentUserText)
                || emailDetector.isExactConfirmation(currentUserText);
        boolean explicitQrCodeRequested = qrCodeDetector.hasIntent(currentUserText);
        boolean explicitDiagramRequested = diagramDetector.hasIntent(currentUserText);
        boolean explicitImageRequested = !explicitQrCodeRequested
                && !explicitDiagramRequested
                && imageDetector.hasIntent(currentUserText);
        boolean hasDesktopReference = referenceStore.hasActiveReference(
                request.conversationId(),
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK);
        DesktopTaskOperation desktopTaskOperation = normalize(hasDesktopReference
                ? desktopTaskDetector.detect(currentUserText, true)
                : desktopTaskDetector.detect(currentUserText));
        boolean hasOcsReference = referenceStore.hasActiveReference(
                request.conversationId(),
                ConversationReferenceStore.ReferenceType.OCS_SESSION);
        boolean hasCourseReference = referenceStore.hasActiveReference(
                request.conversationId(),
                ConversationReferenceStore.ReferenceType.COURSE);
        OcsStudyOperation explicitOcsOperation = normalize(hasOcsReference
                ? learningDetector.detectOcsOperation(currentUserText, true)
                : learningDetector.detectOcsOperation(currentUserText));
        if (explicitOcsOperation == OcsStudyOperation.NONE
                && hasCourseReference
                && learningDetector.hasNaturalCourseStartIntent(currentUserText)) {
            explicitOcsOperation = OcsStudyOperation.START;
        }
        TodoOperation detectedTodoOperation = normalize(todoIntentDetector.detect(currentUserText));
        boolean hasExplicitActionIntent = detectedTodoOperation == TodoOperation.COMPLETE
                || detectedTodoOperation == TodoOperation.CANCEL
                || detectedTodoOperation == TodoOperation.POSTPONE;
        boolean explicitLearningQuery = !hasExplicitActionIntent
                && explicitOcsOperation == OcsStudyOperation.NONE
                && learningDetector.hasLearningQuery(currentUserText);
        boolean fileSelectionRequested = !hasExplicitActionIntent
                && fileBrowseDetector.hasSelectionIntent(currentUserText);
        boolean timeFilteredTaskQuery = todoIntentDetector.hasTimeFilter(currentUserText);
        TodoOperation todoOperation = (explicitLearningQuery
                || explicitOcsOperation != OcsStudyOperation.NONE)
                && !timeFilteredTaskQuery
                && !hasExplicitActionIntent
                ? TodoOperation.NONE
                : detectedTodoOperation;
        boolean directoryListingContinuation = fileBrowseDetector.hasDirectoryListingContinuation(
                currentUserText)
                && hasRecentDirectoryListing(history);
        boolean explicitFileBrowseRequested = !textFileRequested
                && !spreadsheetRequested
                && desktopTaskOperation == DesktopTaskOperation.NONE
                && !fileSelectionRequested
                && todoOperation == TodoOperation.NONE
                && (fileBrowseDetector.hasIntent(currentUserText)
                        || directoryListingContinuation);
        boolean explicitWeatherQuery = weatherDetector.hasQuery(currentUserText);
        boolean hasExplicitCapabilityIntent = explicitSpeechRequested
                || spreadsheetOperation != SpreadsheetOperation.NONE
                || textFileRequested
                || presentationRequested
                || explicitEmailRequested
                || explicitQrCodeRequested
                || explicitDiagramRequested
                || explicitImageRequested
                || desktopTaskOperation != DesktopTaskOperation.NONE
                || explicitOcsOperation != OcsStudyOperation.NONE
                || explicitLearningQuery
                || fileSelectionRequested
                || todoOperation != TodoOperation.NONE
                || explicitFileBrowseRequested
                || explicitWeatherQuery;
        boolean continuationAllowed = !hasExplicitCapabilityIntent;
        boolean speechRequested = explicitSpeechRequested
                || continuationAllowed
                && speechDetector.hasContinuationIntent(history, currentUserText);
        boolean emailSendRequested = explicitEmailRequested
                || continuationAllowed
                && emailDetector.hasIntent(
                        history,
                        currentUserText,
                        emailCapability.hasPendingEmail(request.conversationId()));
        boolean weatherInformationRequested = emailSendRequested && explicitWeatherQuery;
        boolean resolvableWeatherRequested = emailSendRequested
                && weatherDetector.hasResolvableQuery(currentUserText);
        boolean qrCodeRequested = explicitQrCodeRequested
                || continuationAllowed
                && qrCodeDetector.hasContinuationIntent(history, currentUserText);
        boolean diagramRequested = explicitDiagramRequested
                || continuationAllowed
                && diagramDetector.hasContinuationIntent(history, currentUserText);
        boolean imageRequested = explicitImageRequested
                || continuationAllowed
                && imageDetector.hasContinuationIntent(history, currentUserText);
        OcsStudyOperation ocsStudyOperation = explicitOcsOperation;
        if (ocsStudyOperation == OcsStudyOperation.NONE && continuationAllowed) {
            ocsStudyOperation = normalize(
                    learningDetector.detectOcsOperation(history, currentUserText));
        }
        boolean learningQueryRequested = ocsStudyOperation == OcsStudyOperation.NONE
                && !timeFilteredTaskQuery
                && (explicitLearningQuery
                        || continuationAllowed
                        && learningDetector.hasContinuationIntent(history, currentUserText));
        boolean fileBrowseRequested = explicitFileBrowseRequested
                && !emailSendRequested
                && !learningQueryRequested;
        return new AgentIntent(
                speechRequested,
                imageRequested,
                textFileRequested,
                spreadsheetRequested,
                emailSendRequested,
                emailDetector.hasAttachmentIntent(currentUserText),
                emailDetector.isExactConfirmation(currentUserText),
                weatherInformationRequested,
                resolvableWeatherRequested,
                imageReferenceDetector.hasIntent(prompt),
                qrCodeRequested,
                diagramRequested,
                learningQueryRequested,
                ocsStudyOperation,
                voicePreference,
                desktopTaskOperation,
                fileBrowseRequested,
                todoOperation,
                fileSelectionRequested,
                spreadsheetOperation,
                documentRequested,
                presentationRequested);
    }

    private static DesktopTaskOperation normalize(DesktopTaskOperation operation) {
        return operation == null ? DesktopTaskOperation.NONE : operation;
    }

    private static OcsStudyOperation normalize(OcsStudyOperation operation) {
        return operation == null ? OcsStudyOperation.NONE : operation;
    }

    private static TodoOperation normalize(TodoOperation operation) {
        return operation == null ? TodoOperation.NONE : operation;
    }

    private boolean hasRecentDirectoryListing(List<ConversationMessage> history) {
        if (history == null) {
            return false;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message != null && "user".equals(message.role())) {
                return fileBrowseDetector.hasDirectoryListingIntent(message.content());
            }
        }
        return false;
    }
}
