package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailClient;
import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailDeliveryException;
import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailProperties;
import com.weida.bot.wechatilinkbot.model.EmailMessage;
import com.weida.bot.wechatilinkbot.model.EmailAttachment;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-process two-step approval and rate-limit policy for plain-text email delivery.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class EmailCapability {

    private static final String AVAILABLE_REASON_CODE = "available";
    private static final String DISABLED_REASON_CODE = "disabled";
    private static final String SENDER_ADDRESS_MISSING_REASON_CODE = "sender_address_missing";
    private static final String AUTHORIZATION_CODE_MISSING_REASON_CODE =
            "authorization_code_missing";
    private static final String CONVERSATION_ALLOWLIST_MISSING_REASON_CODE =
            "conversation_allowlist_missing";
    private static final String RECIPIENT_ALLOWLIST_MISSING_REASON_CODE =
            "recipient_allowlist_missing";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int MAX_EMAIL_ADDRESS_CHARACTERS = 254;
    private static final int MAX_ATTACHMENTS = 3;
    private static final long MAX_TOTAL_ATTACHMENT_BYTES = 20L * 1024L * 1024L;
    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_SEND_INTENT_PATTERN = Pattern.compile(
            "(?:发|发送|寄|投递).{0,12}(?:邮件|邮箱)"
                    + "|(?:邮件|邮箱).{0,12}(?:发|发送|寄|投递)"
                    + "|\\bsend\\s+(?:an?\\s+)?(?:email|mail)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EXACT_CONFIRMATION_PATTERN = Pattern.compile(
            "^(?:确认发送|确认发邮件|确认发送邮件)[。！!]?$");

    private final QqMailProperties properties;
    private final QqMailClient mailClient;
    private final Clock clock;
    private final Set<String> allowedConversationSet;
    private final Set<String> allowedRecipientSet;
    private final Map<String, PendingEmail> pendingEmailMap = new ConcurrentHashMap<>();
    private final Object pendingApprovalMonitor = new Object();
    private final Object rateLimitMonitor = new Object();
    private final Deque<Instant> globalDeliveryTimeQueue = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> conversationDeliveryTimeQueueMap =
            new HashMap<>();

    @Autowired
    public EmailCapability(QqMailProperties properties, QqMailClient mailClient) {
        this(properties, mailClient, Clock.systemUTC());
    }

    EmailCapability(
            QqMailProperties properties,
            QqMailClient mailClient,
            Clock clock) {
        this.properties = properties;
        this.mailClient = mailClient;
        this.clock = clock;
        this.allowedConversationSet = normalizeAllowedConversationSet(
                properties.getAllowedConversationList());
        this.allowedRecipientSet = normalizeAllowedRecipientSet(
                properties.getAllowedRecipientList());
    }

    public boolean available() {
        return AVAILABLE_REASON_CODE.equals(availabilityReason());
    }

    public String availabilityReason() {
        if (!properties.isEnabled()) {
            return DISABLED_REASON_CODE;
        }
        if (properties.getSenderAddress().isBlank()) {
            return SENDER_ADDRESS_MISSING_REASON_CODE;
        }
        if (properties.getAuthorizationCode().isBlank()) {
            return AUTHORIZATION_CODE_MISSING_REASON_CODE;
        }
        if (allowedConversationSet.isEmpty()) {
            return CONVERSATION_ALLOWLIST_MISSING_REASON_CODE;
        }
        if (allowedRecipientSet.isEmpty()) {
            return RECIPIENT_ALLOWLIST_MISSING_REASON_CODE;
        }
        return AVAILABLE_REASON_CODE;
    }

    public long approvalExpireSeconds() {
        return properties.getApprovalExpireAfter().toSeconds();
    }

    /** Returns whether a conversation still has an unconfirmed email draft. */
    public boolean hasPendingEmail(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        String normalizedConversationId = conversationId.strip();
        removeExpiredPendingEmail(clock.instant());
        return pendingEmailMap.containsKey(normalizedConversationId);
    }

    public EmailMessage prepareEmail(
            String conversationId,
            String currentUserText,
            EmailMessage request) {
        return prepareEmail(conversationId, currentUserText, false, request);
    }

    public EmailMessage prepareEmail(
            String conversationId,
            String currentUserText,
            boolean emailSendRequested,
            EmailMessage request) {
        requireAvailable();
        requireExplicitSendIntent(currentUserText, emailSendRequested);
        String normalizedConversationId = normalizeConversationId(conversationId);
        requireAuthorizedConversation(normalizedConversationId);
        EmailMessage validatedRequest = validateRequest(request);
        Instant now = clock.instant();
        removeExpiredPendingEmail(now);
        synchronized (pendingApprovalMonitor) {
            PendingEmail existing = pendingEmailMap.get(normalizedConversationId);
            if (existing != null) {
                replacePendingEmail(normalizedConversationId, validatedRequest, now, existing);
                return validatedRequest;
            }
            if (pendingEmailMap.size() >= properties.getMaxPendingApprovals()) {
                throw new IllegalArgumentException("待确认邮件数量已达到上限，请稍后再试");
            }
            pendingEmailMap.put(
                    normalizedConversationId,
                    new PendingEmail(validatedRequest, now));
        }
        return validatedRequest;
    }

    private void replacePendingEmail(
            String conversationId,
            EmailMessage request,
            Instant createdTime,
            PendingEmail existing) {
        synchronized (existing) {
            if (existing.isSending()) {
                throw new IllegalArgumentException("上一封邮件正在发送，请勿重复提交");
            }
            pendingEmailMap.put(
                    conversationId,
                    new PendingEmail(request, createdTime));
        }
    }

    public EmailMessage confirmAndSendEmail(
            String conversationId,
            String currentUserText) {
        requireAvailable();
        requireExactConfirmation(currentUserText);
        String normalizedConversationId = normalizeConversationId(conversationId);
        requireAuthorizedConversation(normalizedConversationId);
        PendingEmail pendingEmail = pendingEmailMap.get(normalizedConversationId);
        if (pendingEmail == null) {
            throw new IllegalArgumentException("没有待确认的邮件，请先提供收件人、主题和正文");
        }
        return sendPendingEmail(normalizedConversationId, pendingEmail);
    }

    public void clearPendingEmail(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            pendingEmailMap.remove(conversationId.strip());
        }
    }

    private EmailMessage sendPendingEmail(
            String conversationId,
            PendingEmail pendingEmail) {
        synchronized (pendingEmail) {
            if (pendingEmailMap.get(conversationId) != pendingEmail) {
                throw new IllegalArgumentException("待确认邮件已经失效，请重新提交");
            }
            if (isExpired(pendingEmail, clock.instant())) {
                pendingEmailMap.remove(conversationId, pendingEmail);
                throw new IllegalArgumentException("待确认邮件已过期，请重新提交");
            }
            if (pendingEmail.isSending()) {
                throw new IllegalArgumentException("邮件正在发送，请勿重复确认");
            }
            pendingEmail.setSending(true);
            try {
                reserveDelivery(conversationId);
            } catch (RuntimeException exception) {
                pendingEmail.setSending(false);
                throw exception;
            }
            try {
                mailClient.send(pendingEmail.request());
                return pendingEmail.request();
            } catch (QqMailDeliveryException exception) {
                throw new EmailDeliveryException(exception.getMessage(), exception);
            } finally {
                // SMTP may accept a message before the client observes a failure.
                // Consume the approval after any delivery attempt to prevent blind retries.
                pendingEmailMap.remove(conversationId, pendingEmail);
            }
        }
    }

    private EmailMessage validateRequest(EmailMessage request) {
        if (request == null) {
            throw new IllegalArgumentException("邮件内容不能为空");
        }
        List<String> recipientList = normalizeRecipientList(request.recipientList());
        String subject = normalizeSubject(request.subject());
        String body = normalizeBody(request.body());
        List<EmailAttachment> attachments = validateAttachments(request.attachments());
        return new EmailMessage(recipientList, subject, body, attachments);
    }

    private List<EmailAttachment> validateAttachments(List<EmailAttachment> attachmentList) {
        if (attachmentList == null || attachmentList.isEmpty()) {
            return List.of();
        }
        if (attachmentList.size() > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("邮件附件数量超过限制");
        }
        long totalBytes = 0L;
        for (EmailAttachment attachment : attachmentList) {
            if (attachment == null
                    || attachment.fileName().contains("..")
                    || attachment.fileName().contains("/")
                    || attachment.fileName().contains("\\")
                    || attachment.contentType().contains("\r")
                    || attachment.contentType().contains("\n")) {
                throw new IllegalArgumentException("邮件附件信息不安全");
            }
            totalBytes += attachment.bytes().length;
            if (totalBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("邮件附件总大小超过限制");
            }
        }
        return List.copyOf(attachmentList);
    }

    private List<String> normalizeRecipientList(List<String> rawRecipientList) {
        if (rawRecipientList == null
                || rawRecipientList.isEmpty()
                || rawRecipientList.size() > properties.getMaxRecipients()) {
            throw new IllegalArgumentException("收件人数量为空或超过限制");
        }
        Set<String> recipientSet = new LinkedHashSet<>(rawRecipientList.size());
        for (String rawRecipient : rawRecipientList) {
            String recipient = normalizeEmailAddress(rawRecipient);
            if (!isRecipientAllowed(recipient)) {
                throw new IllegalArgumentException("收件人不在显式 allowlist 中");
            }
            recipientSet.add(recipient);
        }
        return List.copyOf(recipientSet);
    }

    private String normalizeSubject(String rawSubject) {
        String subject = normalizeText(rawSubject);
        if (subject.isBlank()
                || subject.length() > properties.getMaxSubjectCharacters()
                || subject.indexOf('\r') >= 0
                || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("邮件主题为空、超过限制或包含换行");
        }
        return subject;
    }

    private String normalizeBody(String rawBody) {
        String body = normalizeText(rawBody);
        if (body.isBlank() || body.length() > properties.getMaxBodyCharacters()) {
            throw new IllegalArgumentException("邮件正文为空或超过长度限制");
        }
        return body;
    }

    private void reserveDelivery(String conversationId) {
        Instant deliveryTime = clock.instant();
        synchronized (rateLimitMonitor) {
            pruneDeliveryQueues(deliveryTime);
            if (globalDeliveryTimeQueue.size() >= properties.getMaxEmailsPerHour()) {
                throw new IllegalArgumentException("邮件全局发送频率已达到上限，请稍后再试");
            }
            Deque<Instant> conversationQueue = conversationDeliveryTimeQueueMap.get(
                    conversationId);
            int conversationDeliveryCount = conversationQueue == null
                    ? 0
                    : conversationQueue.size();
            if (conversationDeliveryCount
                    >= properties.getMaxEmailsPerConversationPerHour()) {
                throw new IllegalArgumentException("当前会话邮件发送频率已达到上限，请稍后再试");
            }
            if (conversationQueue == null) {
                conversationQueue = new ArrayDeque<>();
                conversationDeliveryTimeQueueMap.put(conversationId, conversationQueue);
            }
            globalDeliveryTimeQueue.addLast(deliveryTime);
            conversationQueue.addLast(deliveryTime);
        }
    }

    private void pruneDeliveryQueues(Instant now) {
        Instant cutoffTime = now.minus(RATE_LIMIT_WINDOW);
        removeBefore(globalDeliveryTimeQueue, cutoffTime);
        List<String> emptyConversationList = new ArrayList<>();
        for (Map.Entry<String, Deque<Instant>> entry
                : conversationDeliveryTimeQueueMap.entrySet()) {
            removeBefore(entry.getValue(), cutoffTime);
            if (entry.getValue().isEmpty()) {
                emptyConversationList.add(entry.getKey());
            }
        }
        emptyConversationList.forEach(conversationDeliveryTimeQueueMap::remove);
    }

    private void removeExpiredPendingEmail(Instant now) {
        pendingEmailMap.entrySet().removeIf(
                entry -> !entry.getValue().isSending() && isExpired(entry.getValue(), now));
    }

    private boolean isExpired(PendingEmail pendingEmail, Instant now) {
        return !now.isBefore(
                pendingEmail.createdTime().plus(properties.getApprovalExpireAfter()));
    }

    private boolean isRecipientAllowed(String recipient) {
        return allowedRecipientSet.contains("*") || allowedRecipientSet.contains(recipient);
    }

    private void requireAuthorizedConversation(String conversationId) {
        if (!allowedConversationSet.contains("*")
                && !allowedConversationSet.contains(conversationId)) {
            throw new IllegalArgumentException("当前会话未获授权使用邮件工具");
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalArgumentException("邮件工具未启用或配置不完整");
        }
    }

    private static void requireExplicitSendIntent(
            String currentUserText,
            boolean emailSendRequested) {
        if (emailSendRequested) {
            return;
        }
        String normalizedText = normalizeText(currentUserText);
        if (!EXPLICIT_SEND_INTENT_PATTERN.matcher(normalizedText).find()) {
            throw new IllegalArgumentException("用户当前消息未明确要求发送邮件");
        }
    }

    private static void requireExactConfirmation(String currentUserText) {
        String normalizedText = normalizeText(currentUserText);
        if (!EXACT_CONFIRMATION_PATTERN.matcher(normalizedText).matches()) {
            throw new IllegalArgumentException("请让用户单独回复“确认发送”后再发送邮件");
        }
    }

    private static String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("会话标识不能为空");
        }
        return conversationId.strip();
    }

    private static String normalizeEmailAddress(String rawAddress) {
        String address = normalizeText(rawAddress).toLowerCase(Locale.ROOT);
        if (address.isBlank()
                || address.length() > MAX_EMAIL_ADDRESS_CHARACTERS
                || !EMAIL_ADDRESS_PATTERN.matcher(address).matches()) {
            throw new IllegalArgumentException("收件人邮箱格式无效");
        }
        return address;
    }

    private static Set<String> normalizeAllowedRecipientSet(List<String> allowedRecipientList) {
        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String rawRecipient : allowedRecipientList) {
            String recipient = normalizeText(rawRecipient).toLowerCase(Locale.ROOT);
            if ("*".equals(recipient)) {
                normalizedSet.add(recipient);
            } else {
                normalizedSet.add(normalizeEmailAddress(recipient));
            }
        }
        return Set.copyOf(normalizedSet);
    }

    private static Set<String> normalizeAllowedConversationSet(
            List<String> allowedConversationList) {
        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String rawConversationId : allowedConversationList) {
            String conversationId = normalizeText(rawConversationId);
            if (!conversationId.isBlank()) {
                normalizedSet.add(conversationId);
            }
        }
        return Set.copyOf(normalizedSet);
    }

    private static String normalizeText(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC);
    }

    private static void removeBefore(Deque<Instant> timeQueue, Instant cutoffTime) {
        while (!timeQueue.isEmpty() && !timeQueue.getFirst().isAfter(cutoffTime)) {
            timeQueue.removeFirst();
        }
    }

    private static final class PendingEmail {

        private final EmailMessage request;
        private final Instant createdTime;
        private volatile boolean sending;

        private PendingEmail(EmailMessage request, Instant createdTime) {
            this.request = request;
            this.createdTime = createdTime;
        }

        private EmailMessage request() {
            return request;
        }

        private Instant createdTime() {
            return createdTime;
        }

        private boolean isSending() {
            return sending;
        }

        private void setSending(boolean sending) {
            this.sending = sending;
        }
    }
}
