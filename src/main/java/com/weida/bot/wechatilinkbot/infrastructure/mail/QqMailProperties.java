package com.weida.bot.wechatilinkbot.infrastructure.mail;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * QQ SMTP credentials, approval limits, and delivery quotas for the email tool.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.mail.qq")
public class QqMailProperties {

    private static final Duration MAX_APPROVAL_EXPIRE_AFTER = Duration.ofHours(1);

    private static final Pattern SENDER_ADDRESS_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    private boolean enabled;

    private String senderAddress = "";

    private String authorizationCode = "";

    private List<String> allowedConversationList = List.of();

    private List<String> allowedRecipientList = List.of();

    @Min(1)
    @Max(10)
    private int maxRecipients = 3;

    @Min(1)
    @Max(200)
    private int maxSubjectCharacters = 100;

    @Min(1)
    @Max(50_000)
    private int maxBodyCharacters = 10_000;

    @NotNull
    private Duration approvalExpireAfter = Duration.ofMinutes(30);

    @Min(1)
    @Max(1_000)
    private int maxPendingApprovals = 100;

    @Min(1)
    @Max(20)
    private int maxEmailsPerConversationPerHour = 3;

    @Min(1)
    @Max(200)
    private int maxEmailsPerHour = 20;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(20);

    @NotNull
    private Duration writeTimeout = Duration.ofSeconds(20);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress == null ? "" : senderAddress.strip();
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode == null
                ? ""
                : authorizationCode.strip();
    }

    public List<String> getAllowedRecipientList() {
        return allowedRecipientList;
    }

    public List<String> getAllowedConversationList() {
        return allowedConversationList;
    }

    public void setAllowedConversationList(List<String> allowedConversationList) {
        this.allowedConversationList = allowedConversationList == null
                ? List.of()
                : List.copyOf(allowedConversationList);
    }

    public void setAllowedRecipientList(List<String> allowedRecipientList) {
        this.allowedRecipientList = allowedRecipientList == null
                ? List.of()
                : List.copyOf(allowedRecipientList);
    }

    public int getMaxRecipients() {
        return maxRecipients;
    }

    public void setMaxRecipients(int maxRecipients) {
        this.maxRecipients = maxRecipients;
    }

    public int getMaxSubjectCharacters() {
        return maxSubjectCharacters;
    }

    public void setMaxSubjectCharacters(int maxSubjectCharacters) {
        this.maxSubjectCharacters = maxSubjectCharacters;
    }

    public int getMaxBodyCharacters() {
        return maxBodyCharacters;
    }

    public void setMaxBodyCharacters(int maxBodyCharacters) {
        this.maxBodyCharacters = maxBodyCharacters;
    }

    public Duration getApprovalExpireAfter() {
        return approvalExpireAfter;
    }

    public void setApprovalExpireAfter(Duration approvalExpireAfter) {
        this.approvalExpireAfter = approvalExpireAfter;
    }

    public int getMaxPendingApprovals() {
        return maxPendingApprovals;
    }

    public void setMaxPendingApprovals(int maxPendingApprovals) {
        this.maxPendingApprovals = maxPendingApprovals;
    }

    public int getMaxEmailsPerConversationPerHour() {
        return maxEmailsPerConversationPerHour;
    }

    public void setMaxEmailsPerConversationPerHour(int maxEmailsPerConversationPerHour) {
        this.maxEmailsPerConversationPerHour = maxEmailsPerConversationPerHour;
    }

    public int getMaxEmailsPerHour() {
        return maxEmailsPerHour;
    }

    public void setMaxEmailsPerHour(int maxEmailsPerHour) {
        this.maxEmailsPerHour = maxEmailsPerHour;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    /** Returns whether an enabled email Tool has a valid sender address. */
    @AssertTrue(message = "邮件工具启用时必须配置有效的 QQ_EMAIL_ADDRESS")
    public boolean isSenderAddressConfigurationValid() {
        return !enabled || isNonBlankValidEmail(senderAddress);
    }

    private static boolean isNonBlankValidEmail(String value) {
        return !value.isBlank() && SENDER_ADDRESS_PATTERN.matcher(value).matches();
    }

    /** Returns whether an enabled email Tool has an SMTP authorization code. */
    @AssertTrue(message = "邮件工具启用时必须配置 QQ_SMTP_IMAP")
    public boolean isAuthorizationCodeConfigurationValid() {
        return !enabled || !authorizationCode.isBlank();
    }

    /** Returns whether an enabled email Tool has an explicit conversation allowlist. */
    @AssertTrue(message = "邮件工具启用时必须配置 BOT_EMAIL_ALLOWED_CONVERSATIONS")
    public boolean isConversationAllowlistConfigurationValid() {
        return !enabled || hasNonBlankValue(allowedConversationList);
    }

    /** Returns whether an enabled email Tool has an explicit recipient allowlist. */
    @AssertTrue(message = "邮件工具启用时必须配置 BOT_EMAIL_ALLOWED_RECIPIENTS")
    public boolean isRecipientAllowlistConfigurationValid() {
        return !enabled || hasNonBlankValue(allowedRecipientList);
    }

    /**
     * Requires all positive duration limits to avoid unbounded network or approval waits.
     *
     * @return whether durations are positive
     */
    @AssertTrue(message = "邮件工具的审批有效期必须在 1 秒到 1 小时之间，SMTP 超时必须大于 0")
    public boolean isDurationConfigurationValid() {
        return isApprovalExpiryValid(approvalExpireAfter)
                && isPositive(connectTimeout)
                && isPositive(readTimeout)
                && isPositive(writeTimeout);
    }

    private static boolean isApprovalExpiryValid(Duration duration) {
        return isPositive(duration) && duration.compareTo(MAX_APPROVAL_EXPIRE_AFTER) <= 0;
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static boolean hasNonBlankValue(List<String> valueList) {
        return valueList.stream().anyMatch(
                value -> value != null && !value.isBlank());
    }
}
