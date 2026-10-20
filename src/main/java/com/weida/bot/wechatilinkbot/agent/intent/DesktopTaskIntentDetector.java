package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects only explicit desktop-task requests; generic chat never exposes desktop tools. */
@Component
public class DesktopTaskIntentDetector {

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("dt-[0-9a-f-]{8,}");
    private static final Pattern CONFIRM_PATTERN = Pattern.compile(
            "^(?:确认|同意|执行)\\s*(?:任务)?\\s*dt-[0-9a-f-]{8,}[。！!]?$");
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "(?:查看|查询|检查).{0,8}(?:任务)?.{0,8}(?:状态|进度).{0,24}dt-[0-9a-f-]{8,}"
                    + "|dt-[0-9a-f-]{8,}.{0,12}(?:状态|进度)");
    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "^(?:取消|撤销|终止)\\s*(?:任务)?\\s*dt-[0-9a-f-]{8,}[。！!]?$");
    private static final Pattern NATURAL_CONFIRM_PATTERN = Pattern.compile(
            "^(?:确认|同意|执行)(?:打开|执行)?\\s*(?:刚才(?:的)?(?:任务|那个)?|"
                    + "这个(?:任务)?|打开)?[。！!]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NATURAL_STATUS_PATTERN = Pattern.compile(
            "(?:刚才(?:的)?(?:任务|那个)|这个(?:任务)?).{0,8}(?:状态|进度|怎么样)"
                    + "|(?:状态|进度|怎么样).{0,8}(?:刚才(?:的)?(?:任务|那个)|这个(?:任务)?)"
                    + "|^(?:查看|查询)?\\s*(?:状态|进度)(?:怎么样|如何|呢)?[。！!？?]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NATURAL_CANCEL_PATTERN = Pattern.compile(
            "^(?:取消|撤销|终止)\\s*(?:刚才(?:的)?(?:任务|那个)|这个(?:任务)?)[。！!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OPEN_URL_PATTERN = Pattern.compile(
            "(?:打开|访问|进入|浏览).{0,24}https://[^\\s]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_QQ_MAIL_PATTERN = Pattern.compile(
            "(?:打开|访问|进入|浏览).{0,24}(?:我的)?qq\\s*(?:邮箱|邮件)",
            Pattern.CASE_INSENSITIVE);

    public DesktopTaskOperation detect(String text) {
        return detect(text, false);
    }

    public DesktopTaskOperation detect(String text, boolean allowNaturalReference) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return DesktopTaskOperation.NONE;
        }
        if (CANCEL_PATTERN.matcher(normalized).matches()
                || allowNaturalReference && NATURAL_CANCEL_PATTERN.matcher(normalized).matches()) {
            return DesktopTaskOperation.CANCEL;
        }
        if (CONFIRM_PATTERN.matcher(normalized).matches()
                || allowNaturalReference && NATURAL_CONFIRM_PATTERN.matcher(normalized).matches()) {
            return DesktopTaskOperation.CONFIRM;
        }
        if (STATUS_PATTERN.matcher(normalized).find()
                || allowNaturalReference && NATURAL_STATUS_PATTERN.matcher(normalized).find()) {
            return DesktopTaskOperation.STATUS;
        }
        if (OPEN_URL_PATTERN.matcher(normalized).find()
                || OPEN_QQ_MAIL_PATTERN.matcher(normalized).find()) {
            return DesktopTaskOperation.REQUEST_OPEN_URL;
        }
        return DesktopTaskOperation.NONE;
    }

    public boolean hasTaskId(String text) {
        return TASK_ID_PATTERN.matcher(normalize(text)).find();
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
    }
}
