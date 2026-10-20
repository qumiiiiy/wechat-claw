package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.model.DesktopTaskStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Validates trusted conversation identity, task state, and URL policy before local execution. */
@Component
public class DesktopTaskPolicy {

    private static final int MAX_URL_CHARACTERS = 2_048;

    private final DesktopTaskProperties properties;

    public DesktopTaskPolicy(DesktopTaskProperties properties) {
        this.properties = properties;
    }

    public void validateFeatureEnabled() {
        if (!properties.isEnabled()) {
            throw new DesktopTaskPolicyViolationException("电脑远程任务功能当前未启用");
        }
    }

    public Duration approvalExpireAfter() {
        return properties.getApprovalExpireAfter();
    }

    public void validateConversation(String conversationId) {
        String safeConversationId = requireText(conversationId, "微信会话不能为空");
        Set<String> restrictedConversationIds = configuredConversationIdSet();
        if (!restrictedConversationIds.isEmpty()
                && !restrictedConversationIds.contains(safeConversationId)) {
            throw new DesktopTaskPolicyViolationException("当前微信用户没有操作本机电脑的权限");
        }
    }

    public URI normalizeAndValidateUrl(String rawUrl) {
        String safeUrl = requireText(rawUrl, "网址不能为空");
        if (safeUrl.length() > MAX_URL_CHARACTERS) {
            throw new DesktopTaskPolicyViolationException("网址长度超过限制");
        }
        try {
            URI uri = new URI(safeUrl).normalize();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || host.isBlank()
                    || uri.getUserInfo() != null
                    || uri.isOpaque()
                    || uri.getPort() == 0
                    || uri.getPort() > 65_535) {
                throw new DesktopTaskPolicyViolationException("仅允许格式正确且不包含账号信息的 HTTPS 网址");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            Set<String> restrictedHosts = configuredUrlHostSet();
            if (!restrictedHosts.isEmpty() && !restrictedHosts.contains(normalizedHost)) {
                throw new DesktopTaskPolicyViolationException("该网址主机不在本机允许列表中");
            }
            return new URI(
                    "https",
                    null,
                    normalizedHost,
                    uri.getPort(),
                    uri.getRawPath(),
                    uri.getRawQuery(),
                    uri.getRawFragment());
        } catch (URISyntaxException exception) {
            throw new DesktopTaskPolicyViolationException("网址格式不正确");
        }
    }

    public void validateApproval(DesktopTask task, String conversationId) {
        validateTaskOwner(task, conversationId);
        if (task.status() != DesktopTaskStatus.WAITING_APPROVAL) {
            throw new DesktopTaskPolicyViolationException("该任务当前不能确认执行");
        }
    }

    public void validateCancellation(DesktopTask task, String conversationId) {
        validateTaskOwner(task, conversationId);
        if (task.status() != DesktopTaskStatus.WAITING_APPROVAL) {
            throw new DesktopTaskPolicyViolationException("该任务当前不能取消");
        }
    }

    private void validateTaskOwner(DesktopTask task, String conversationId) {
        validateConversation(conversationId);
        if (!task.conversationId().equals(requireText(conversationId, "微信会话不能为空"))) {
            throw new DesktopTaskPolicyViolationException("只能操作本人创建的电脑任务");
        }
    }

    private Set<String> configuredConversationIdSet() {
        return properties.getAllowedConversationList().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> configuredUrlHostSet() {
        return properties.getAllowedUrlHostList().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DesktopTaskPolicyViolationException(message);
        }
        return value.strip();
    }
}
