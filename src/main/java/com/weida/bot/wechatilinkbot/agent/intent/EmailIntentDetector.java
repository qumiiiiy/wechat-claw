package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit email delivery requests and bounded follow-up turns.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class EmailIntentDetector {

    private static final int MAX_PREVIOUS_USER_TURNS = 4;
    private static final String EMAIL_ADDRESS_REGEX =
            "[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,63}";
    private static final Pattern EXPLICIT_SEND_PATTERN = Pattern.compile(
            "(?:发|发送|寄|投递).{0,12}(?:邮件|邮箱)"
                    + "|(?:邮件|邮箱).{0,12}(?:发|发送|寄|投递)"
                    + "|(?:发送|发|寄|投递)(?:给|到|至)?.{0,80}"
                    + EMAIL_ADDRESS_REGEX
                    + "|(?:写|代写|帮我写).{0,24}(?:邮件|邮箱).{0,80}"
                    + EMAIL_ADDRESS_REGEX
                    + "|(?:代|替).{0,8}(?:我|本人).{0,8}(?:向|给).{0,80}"
                    + EMAIL_ADDRESS_REGEX
                    + ".{0,32}(?:问好|问候|道声|转达|带(?:个|句)?话)"
                    + "|\\bsend\\s+(?:an?\\s+)?(?:email|mail)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EXACT_CONFIRMATION_PATTERN = Pattern.compile(
            "^(?:确认发送|确认发邮件|确认发送邮件)[。！!]?$");
    private static final Pattern CONTINUATION_PATTERN = Pattern.compile(
            "(?:就是|补充|修改|润色|重写|改成|改为|改一下|调整|"
                    + "(?:重新|再)(?:写|改|润色|调整|准备|生成)|"
                    + "文采|措辞|语气|主题|正文|收件人|邮箱|称呼|姓|"
                    + "爱称|先生|女士|天气|城市|地点|口吻|署名|落款|"
                    + "工作|项目|情况|细节|歉意|道歉|主人|产品|关系|确认|"
                    + "整体|强调|游戏|去掉|删(?:除|掉)?|增加|减少|多一点|少一点|"
                    + "表情|颜文字|行文|现在|晚上)");
    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "(?:取消|不要|不用|别).{0,8}(?:发送|发|邮件|邮箱)");
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile(
            "(?:附件|附上|附加|pdf|docx|xlsx|excel|电子表格|工作簿|文件|文档)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_ATTACHMENT_PATTERN = Pattern.compile(
            "(?:无|没有|不要|不需要|无需).{0,4}(?:附件|附加|文件|文档)"
                    + "|(?:附件|附加|文件|文档).{0,4}(?:无|没有|不要|不需要|无需)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns whether the current turn belongs to an explicit email-send workflow.
     *
     * @param history bounded conversation history
     * @param currentText current normalized user text
     * @return {@code true} only for an explicit request or a recognizable nearby follow-up
     */
    public boolean hasIntent(
            List<ConversationMessage> history,
            String currentText) {
        return hasIntent(history, currentText, true);
    }

    /**
     * Returns whether the current turn belongs to email delivery, optionally allowing
     * continuation of an existing draft.
     */
    public boolean hasIntent(
            List<ConversationMessage> history,
            String currentText,
            boolean draftContinuationAllowed) {
        String normalizedText = normalize(currentText);
        if (normalizedText.isBlank() || CANCEL_PATTERN.matcher(normalizedText).find()) {
            return false;
        }
        if (hasExplicitSendIntent(normalizedText) || isExactConfirmation(normalizedText)) {
            return true;
        }
        if (!CONTINUATION_PATTERN.matcher(normalizedText).find()) {
            return false;
        }
        // A prepared draft is an independent, per-conversation state. Its edits must
        // remain in the email workflow even after the original request rolls out of
        // bounded chat history. Without a draft, retain the narrow nearby-history
        // check so unrelated messages cannot activate the delivery tool.
        return draftContinuationAllowed || hasRecentExplicitRequest(history);
    }

    /** Returns whether the current message is the exact approval phrase. */
    public boolean isExactConfirmation(String text) {
        return EXACT_CONFIRMATION_PATTERN.matcher(normalize(text)).matches();
    }

    /** Returns whether one message explicitly asks for email delivery. */
    public boolean hasExplicitSendIntent(String text) {
        return EXPLICIT_SEND_PATTERN.matcher(normalize(text)).find();
    }

    /** Returns whether the user asks to include a generated file in the email. */
    public boolean hasAttachmentIntent(String text) {
        String normalizedText = normalize(text);
        return !NO_ATTACHMENT_PATTERN.matcher(normalizedText).find()
                && ATTACHMENT_PATTERN.matcher(normalizedText).find();
    }

    private boolean hasRecentExplicitRequest(List<ConversationMessage> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int userTurnCount = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message == null) {
                continue;
            }
            if ("assistant".equals(message.role())
                    && message.content() != null
                    && message.content().contains("邮件已发送")) {
                return false;
            }
            if (!"user".equals(message.role())) {
                continue;
            }
            userTurnCount++;
            if (hasExplicitSendIntent(message.content())) {
                return true;
            }
            if (userTurnCount >= MAX_PREVIOUS_USER_TURNS) {
                return false;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC);
    }
}
