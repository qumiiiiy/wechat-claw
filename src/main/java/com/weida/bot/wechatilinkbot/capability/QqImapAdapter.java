package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailProperties;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.NotTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Read-only IMAP adapter for QQ Mail that fetches unread and starred emails
 * as {@link PendingItem} entries for the unified todo view.
 *
 * <p>Opens the INBOX in {@link Folder#READ_ONLY} — never modifies read,
 * starred, moved, or deleted state. Connection and result limits are bounded.</p>
 */
@Component
@ConditionalOnProperty(prefix = "bot.infrastructure.mail.qq", name = "enabled", havingValue = "true")
public class QqImapAdapter implements PendingItemAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(QqImapAdapter.class);

    private static final String IMAP_HOST = "imap.qq.com";
    private static final int IMAP_SSL_PORT = 993;
    private static final int MAX_MESSAGES = 50;
    private static final int MAX_SUMMARY_CHARS = 200;
    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long READ_TIMEOUT_MS = 15_000;

    private final QqMailProperties properties;
    private final EmailActionClassifierPort actionClassifier;

    public QqImapAdapter(QqMailProperties properties) {
        this(properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public QqImapAdapter(
            QqMailProperties properties,
            EmailActionClassifierPort actionClassifier) {
        this.properties = properties;
        this.actionClassifier = actionClassifier;
    }

    @Override
    public PendingItemSource source() {
        return PendingItemSource.EMAIL;
    }

    @Override
    public List<PendingItem> fetchItems(String conversationId) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String address = properties.getSenderAddress();
        String authCode = properties.getAuthorizationCode();
        if (address.isBlank() || authCode.isBlank()) {
            LOG.debug("qq_imap_skipped reason=no_credentials");
            return List.of();
        }

        Store store = null;
        try {
            Session session = createSession();
            store = session.getStore("imap");
            store.connect(IMAP_HOST, IMAP_SSL_PORT, address, authCode);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // Try unread + starred search first; fall back to recent N if IMAP flags fail
            Message[] messages = searchUnreadOrStarred(inbox);
            if (messages.length == 0) {
                LOG.debug("qq_imap_flag_search_empty falling_back_to_recent");
                messages = fetchRecent(inbox);
            }
            List<EmailEnvelope> envelopes = new ArrayList<>();
            for (Message message : messages) {
                try {
                    envelopes.add(toEnvelope(message));
                } catch (Exception e) {
                    LOG.debug("qq_imap_parse_message_skipped", e);
                }
            }
            List<Boolean> modelActionableFlags = classifyWithModel(envelopes);
            List<PendingItem> items = new ArrayList<>();
            for (int index = 0; index < envelopes.size(); index++) {
                try {
                    Boolean modelActionable = modelActionableFlags == null
                            ? null
                            : modelActionableFlags.get(index);
                    PendingItem item = toPendingItem(envelopes.get(index), modelActionable);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    LOG.debug("qq_imap_parse_message_skipped", e);
                }
            }
            int parsed = items.size();
            items = items.stream()
                    .filter(item -> item.note() == null
                            || !item.note().equals("已过滤"))
                    .toList();
            LOG.info("qq_imap_fetch_completed parsed={} afterFilter={} noise={}",
                    parsed, items.size(), parsed - items.size());
            return items;
        } catch (Exception e) {
            LOG.warn("qq_imap_fetch_failed reason={}", e.getMessage());
            return List.of();
        } finally {
            if (store != null) {
                try { store.close(); } catch (MessagingException ignored) { }
            }
        }
    }

    @Override
    public boolean supportsModify() {
        return false;
    }

    // ── private helpers ────────────────────────────────────────────

    private static Session createSession() {
        Properties props = new Properties();
        props.setProperty("mail.imap.ssl.enable", "true");
        props.setProperty("mail.imap.ssl.checkserveridentity", "true");
        props.setProperty("mail.imap.connectiontimeout",
                Long.toString(CONNECT_TIMEOUT_MS));
        props.setProperty("mail.imap.timeout",
                Long.toString(READ_TIMEOUT_MS));
        props.setProperty("mail.store.protocol", "imap");
        return Session.getInstance(props);
    }

    /**
     * Searches for unread or starred messages. Uses NotTerm for UNSEEN
     * which is more compatible with QQ Mail than FlagTerm(SEEN, false).
     * Falls back gracefully if the IMAP server doesn't support search.
     */
    private static Message[] searchUnreadOrStarred(Folder inbox) throws MessagingException {
        try {
            SearchTerm unread = new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true));
            SearchTerm starred = new FlagTerm(new Flags(Flags.Flag.FLAGGED), true);
            Message[] results = inbox.search(new OrTerm(unread, starred));
            // Pre-fetch envelope headers
            if (results.length > 0) {
                FetchProfile headersOnly = new FetchProfile();
                headersOnly.add(FetchProfile.Item.ENVELOPE);
                inbox.fetch(results, headersOnly);
            }
            return results;
        } catch (MessagingException e) {
            LOG.debug("qq_imap_search_failed falling_back", e);
            return new Message[0];
        }
    }

    /**
     * Fetches the most recent messages from INBOX, newest first.
     * Uses FetchProfile to pre-load only envelope data (FROM, SUBJECT, DATE)
     * without downloading message bodies — the classifier works on subjects.
     */
    private static Message[] fetchRecent(Folder inbox) throws MessagingException {
        int count = inbox.getMessageCount();
        if (count <= 0) {
            return new Message[0];
        }
        int start = Math.max(1, count - MAX_MESSAGES + 1);
        Message[] messages = inbox.getMessages(start, count);
        // Pre-fetch envelope headers only — skip body download
        FetchProfile headersOnly = new FetchProfile();
        headersOnly.add(FetchProfile.Item.ENVELOPE);
        inbox.fetch(messages, headersOnly);
        // Reverse so newest first
        for (int i = 0, j = messages.length - 1; i < j; i++, j--) {
            Message tmp = messages[i];
            messages[i] = messages[j];
            messages[j] = tmp;
        }
        return messages;
    }

    private static PendingItem toPendingItem(
            EmailEnvelope envelope,
            Boolean modelActionable) {
        String title = envelope.subject();
        String internalId = envelope.internalId();
        String from = envelope.from();
        if (!from.isEmpty()) {
            title = "[" + from + "] " + envelope.subject();
        }

        // Extract a future deadline from the subject; fall back to receivedAt
        Instant dueTime = extractDeadline(envelope.subject(), envelope.receivedAt());

        boolean actionable = modelActionable != null
                ? modelActionable
                : EmailActionClassifier.classify(
                                envelope.subject(),
                                "",
                                envelope.receivedAt())
                        == EmailActionClassifier.Result.ACTION_REQUIRED;
        String note = actionable ? null : "已过滤";

        return new PendingItem(
                0, internalId, truncate(title, 200),
                PendingItemSource.EMAIL, StudentTodoStatus.PENDING, dueTime,
                note);
    }

    private static EmailEnvelope toEnvelope(Message message) throws MessagingException {
        String subject = message.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = "(无主题)";
        }
        try {
            subject = MimeUtility.decodeText(subject);
        } catch (java.io.UnsupportedEncodingException e) {
            // Keep original subject if decoding fails
        }
        return new EmailEnvelope(
                subject,
                extractReceivedAt(message),
                extractFrom(message),
                extractMessageId(message));
    }

    /**
     * Asks the model port to classify the whole batch once; falls back to {@code null} so the
     * caller uses the deterministic rule classifier per message.
     */
    private List<Boolean> classifyWithModel(List<EmailEnvelope> envelopes) {
        if (actionClassifier == null || !actionClassifier.available() || envelopes.isEmpty()) {
            return null;
        }
        List<String> subjects = envelopes.stream()
                .map(EmailEnvelope::subject)
                .toList();
        try {
            List<EmailActionClassifierPort.Decision> decisions =
                    actionClassifier.classify(subjects);
            if (decisions == null || decisions.size() != subjects.size()) {
                LOG.warn("qq_imap_model_classification_mismatch falling_back_to_rules");
                return null;
            }
            return decisions.stream()
                    .map(EmailActionClassifierPort.Decision::actionable)
                    .toList();
        } catch (RuntimeException exception) {
            LOG.warn("qq_imap_model_classification_failed falling_back_to_rules");
            return null;
        }
    }

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    // Date patterns ordered by specificity: "8月7日16:00", "2026年8月7日", "8/7 15:00"
    private static final Pattern[] DEADLINE_PATTERNS = {
            Pattern.compile("(\\d{4})\\s*[年/-]\\s*(\\d{1,2})\\s*[月/-]\\s*(\\d{1,2})\\s*[日号]?\\s*(\\d{1,2}):(\\d{2})"),
            Pattern.compile("(\\d{1,2})\\s*[月/]\\s*(\\d{1,2})\\s*[日号]?\\s*(\\d{1,2}):(\\d{2})"),
            Pattern.compile("(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})[日号]?"),
            Pattern.compile("(\\d{1,2})[月/](\\d{1,2})[日号]?"),
    };

    /**
     * Tries to extract a future deadline from the email subject.
     * Falls back to {@code receivedAt} if no future date is found.
     */
    private static Instant extractDeadline(String subject, Instant receivedAt) {
        for (Pattern pattern : DEADLINE_PATTERNS) {
            Matcher m = pattern.matcher(subject);
            if (m.find()) {
                try {
                    Instant extracted = parseDeadline(m);
                    if (extracted != null && extracted.isAfter(Instant.now())) {
                        return extracted;
                    }
                } catch (DateTimeParseException ignored) {
                    // Continue to next pattern
                }
            }
        }
        return receivedAt;
    }

    private static Instant parseDeadline(Matcher m) {
        int year, month, day, hour = 23, minute = 59;
        int groups = m.groupCount();
        if (groups >= 5) {
            // Full datetime: year(opt) month day hour minute
            int gi = 1;
            if (m.group(1).length() == 4) {
                year = Integer.parseInt(m.group(1));
                gi = 2;
            } else {
                year = LocalDate.now(SHANGHAI).getYear();
            }
            month = Integer.parseInt(m.group(gi));
            day = Integer.parseInt(m.group(gi + 1));
            hour = Integer.parseInt(m.group(gi + 2));
            minute = Integer.parseInt(m.group(gi + 3));
            return LocalDate.of(year, month, day)
                    .atTime(LocalTime.of(hour, minute))
                    .atZone(SHANGHAI).toInstant();
        } else if (groups >= 2) {
            // Date only: year(opt) month day → default to end of day
            if (m.group(1).length() == 4) {
                year = Integer.parseInt(m.group(1));
                month = Integer.parseInt(m.group(2));
                day = Integer.parseInt(m.group(3));
            } else {
                year = LocalDate.now(SHANGHAI).getYear();
                month = Integer.parseInt(m.group(1));
                day = Integer.parseInt(m.group(2));
            }
            return LocalDate.of(year, month, day)
                    .atTime(LocalTime.of(23, 59))
                    .atZone(SHANGHAI).toInstant();
        }
        return null;
    }

    private static String extractFrom(Message message) throws MessagingException {
        var addresses = message.getFrom();
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        // Use InternetAddress.getPersonal() which handles RFC 2047 decoding
        if (addresses[0] instanceof jakarta.mail.internet.InternetAddress ia) {
            String personal = ia.getPersonal();
            if (personal != null && !personal.isBlank()) {
                return personal.trim();
            }
            String addr = ia.getAddress();
            if (addr != null) {
                return addr.trim();
            }
        }
        // Fallback: MimeUtility.decodeText for encoded raw strings
        String raw = addresses[0].toString();
        try {
            raw = MimeUtility.decodeText(raw);
        } catch (java.io.UnsupportedEncodingException e) {
            // Keep raw
        }
        int lt = raw.indexOf('<');
        if (lt >= 0) {
            String name = raw.substring(0, lt).trim();
            if (!name.isEmpty() && !name.equals("\"\"") && !name.equals(raw)) {
                return name.replace("\"", "").trim();
            }
            return raw.substring(lt + 1, raw.indexOf('>', lt)).trim();
        }
        return raw.trim();
    }

    private static Instant extractReceivedAt(Message message) throws MessagingException {
        Date received = message.getReceivedDate();
        if (received == null) {
            received = message.getSentDate();
        }
        if (received == null) {
            return Instant.now();
        }
        return received.toInstant();
    }

    private static String extractMessageId(Message message) throws MessagingException {
        String[] ids = message.getHeader("Message-ID");
        if (ids != null && ids.length > 0 && !ids[0].isBlank()) {
            return ids[0].trim();
        }
        // Fallback: use a hash of from+subject+received date
        String fallback = extractFrom(message) + "|" + message.getSubject() + "|"
                + extractReceivedAt(message).toEpochMilli();
        return "email-" + Integer.toHexString(fallback.hashCode());
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "…";
    }

    private record EmailEnvelope(
            String subject,
            Instant receivedAt,
            String from,
            String internalId) {
    }
}
