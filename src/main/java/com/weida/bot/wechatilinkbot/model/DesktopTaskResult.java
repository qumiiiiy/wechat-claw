package com.weida.bot.wechatilinkbot.model;

/** Stable, non-sensitive result returned by a local desktop action. */
public record DesktopTaskResult(String code, String summary) {

    public DesktopTaskResult {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("桌面任务结果 code 不能为空");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("桌面任务结果摘要不能为空");
        }
        code = code.strip();
        summary = summary.strip();
    }

    public static DesktopTaskResult succeeded(String summary) {
        return new DesktopTaskResult("opened", summary);
    }

    public static DesktopTaskResult failed(String code, String summary) {
        return new DesktopTaskResult(code, summary);
    }
}
