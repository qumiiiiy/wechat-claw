package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Opt-in desktop-task limits and optional operator restrictions. */
@Validated
@ConfigurationProperties(prefix = "bot.capability.desktop")
public class DesktopTaskProperties {

    private static final Duration MAX_APPROVAL_EXPIRE_AFTER = Duration.ofHours(1);

    private boolean enabled;
    private List<String> allowedConversationList = List.of();
    private List<String> allowedUrlHostList = List.of();

    @NotNull
    private Duration approvalExpireAfter = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedConversationList() {
        return allowedConversationList;
    }

    public void setAllowedConversationList(List<String> allowedConversationList) {
        this.allowedConversationList = copy(allowedConversationList);
    }

    public List<String> getAllowedUrlHostList() {
        return allowedUrlHostList;
    }

    public void setAllowedUrlHostList(List<String> allowedUrlHostList) {
        this.allowedUrlHostList = copy(allowedUrlHostList);
    }

    public Duration getApprovalExpireAfter() {
        return approvalExpireAfter;
    }

    public void setApprovalExpireAfter(Duration approvalExpireAfter) {
        this.approvalExpireAfter = approvalExpireAfter;
    }

    @AssertTrue(message = "桌面任务审批有效期必须在 1 秒到 1 小时之间")
    public boolean isApprovalExpiryValid() {
        return approvalExpireAfter != null
                && !approvalExpireAfter.isZero()
                && !approvalExpireAfter.isNegative()
                && approvalExpireAfter.compareTo(MAX_APPROVAL_EXPIRE_AFTER) <= 0;
    }

    private static List<String> copy(List<String> valueList) {
        return valueList == null ? List.of() : List.copyOf(valueList);
    }

}
