package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.START_OCS_STUDY;

import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.OcsLoginQrRelay;
import com.weida.bot.wechatilinkbot.model.OcsPluginStatus;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import com.weida.bot.wechatilinkbot.tool.input.StartOcsStudyInput;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates one server-owned command for the local OCS companion. */
@Order(81)
@Component
@ConditionalOnBean(OcsStudyCapability.class)
public class StartOcsStudyTool implements AgentTool<StartOcsStudyInput> {

    private static final long COMPANION_POLL_INTERVAL_MS = 2000;
    private static final int COMPANION_POLL_MAX_ATTEMPTS = 10;
    private static final long LOGIN_QR_POLL_INTERVAL_MS = 3000;
    private static final int LOGIN_QR_POLL_MAX_ATTEMPTS = 10;

    private final OcsStudyCapability studyCapability;

    public StartOcsStudyTool(OcsStudyCapability studyCapability) {
        this.studyCapability = studyCapability;
    }

    @Override
    public String name() {
        return START_OCS_STUDY;
    }

    @Override
    public String description() {
        return "仅在用户本轮明确要求使用 OCS 开始、继续或观看网课时使用。"
                + "提取课程名称；若最近课程列表或详情给出了 C1、C17 等引用，必须同时传 courseReference。"
                + "用户明确提到章节时（如「看第三章」「打开绪论」），把章节名称填入 chapterName。"
                + "不得索要课程网址。工具会打开本机学习通课程列表，伴随脚本优先按引用精确定位，"
                + "没有引用时才按名称定位；随后自动进入指定章节或第一个可学章节并上报真实状态。";
    }

    @Override
    public Class<StartOcsStudyInput> inputType() {
        return StartOcsStudyInput.class;
    }

    @Override
    public boolean available() {
        return studyCapability.available();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return true;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(StartOcsStudyInput input, ToolContext context) {
        OcsStudySession session = studyCapability.startSession(
                context.conversationId(),
                input.courseName(),
                input.courseReference(),
                input.chapterName());
        if (session.status() == OcsStudySession.Status.FAILED) {
            return new ToolExecution(
                    "OCS 学习会话 " + session.sessionId()
                            + " 启动失败：" + session.lastEvent(),
                    null);
        }

        // Poll for companion connection
        String sessionId = session.sessionId();
        String owner = context.conversationId();
        OcsStudySession current = session;
        for (int i = 0; i < COMPANION_POLL_MAX_ATTEMPTS; i++) {
            sleep(COMPANION_POLL_INTERVAL_MS);
            current = studyCapability.getStatus(owner, sessionId);
            if (current.status() != OcsStudySession.Status.CREATED) {
                break;
            }
        }

        // Handle WAITING_LOGIN: poll for QR code availability
        if (current.status() == OcsStudySession.Status.WAITING_LOGIN) {
            return waitForLoginQr(owner, sessionId, current);
        }

        // Handle PLUGIN_MISSING
        if (current.status() == OcsStudySession.Status.PLUGIN_MISSING) {
            return new ToolExecution(formatSessionStatus(current)
                    + "\n请确认浏览器 Tampermonkey 中已安装 OCS 插件后重试。", null);
        }

        String pluginNote = formatPluginNote(current.pluginStatus());
        return new ToolExecution(
                "OCS 学习会话 " + sessionId + " 已启动。" + pluginNote
                        + "之后可询问“OCS 学习会话 " + sessionId + " 的进度”。",
                null);
    }

    private ToolExecution waitForLoginQr(String owner, String sessionId, OcsStudySession current) {
        for (int i = 0; i < LOGIN_QR_POLL_MAX_ATTEMPTS; i++) {
            OcsLoginQrRelay qr = studyCapability.getLoginQrRelay(sessionId);
            if (qr != null) {
                byte[] qrBytes = decodeDataUrl(qr.qrDataUrl());
                if (qrBytes != null && qrBytes.length > 0) {
                    GeneratedMedia media = new GeneratedMedia(
                            GeneratedMediaKind.IMAGE,
                            qrBytes,
                            "login-qr.png",
                            0L,
                            "学习通登录二维码");
                    return new ToolExecution(
                            formatSessionStatus(current)
                                    + "\n浏览器需要登录学习通，请用超星 APP 扫描下方二维码。"
                                    + "\n扫码后程序会自动继续。",
                            media);
                }
            }
            sleep(LOGIN_QR_POLL_INTERVAL_MS);
            current = studyCapability.getStatus(owner, sessionId);
            if (current.status() != OcsStudySession.Status.WAITING_LOGIN) {
                break;
            }
        }
        return new ToolExecution(formatSessionStatus(current), null);
    }

    private static String formatSessionStatus(OcsStudySession session) {
        String pluginNote = formatPluginNote(session.pluginStatus());
        return "OCS 学习会话 " + session.sessionId()
                + " 当前状态：" + session.status() + pluginNote;
    }

    private static String formatPluginNote(OcsPluginStatus pluginStatus) {
        if (pluginStatus == null || !pluginStatus.detected()) {
            return "";
        }
        String config = pluginStatus.configApplied() ? "配置已生效。" : "配置未完全生效。";
        return " OCS 插件已检测到，" + config;
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0 || !dataUrl.substring(0, commaIndex).contains("base64")) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        studyCapability.clearConversation(conversationId);
    }
}
