package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_OCS_STUDY_STATUS;

import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.OcsLoginQrRelay;
import com.weida.bot.wechatilinkbot.model.OcsPluginStatus;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import com.weida.bot.wechatilinkbot.tool.input.OcsStudySessionIdInput;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Returns actual progress and bounded notes reported by the local companion. */
@Order(82)
@Component
@ConditionalOnBean(OcsStudyCapability.class)
public class GetOcsStudyStatusTool implements AgentTool<OcsStudySessionIdInput> {

    private final OcsStudyCapability studyCapability;

    public GetOcsStudyStatusTool(OcsStudyCapability studyCapability) {
        this.studyCapability = studyCapability;
    }

    @Override
    public String name() {
        return GET_OCS_STUDY_STATUS;
    }

    @Override
    public String description() {
        return "仅在用户本轮明确查询此前 OCS 学习会话的状态、进度或笔记时使用。"
                + "必须使用 start_ocs_study 返回的会话 ID，不能访问其他微信会话创建的任务。";
    }

    @Override
    public Class<OcsStudySessionIdInput> inputType() {
        return OcsStudySessionIdInput.class;
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
    public ToolExecution execute(OcsStudySessionIdInput input, ToolContext context) {
        OcsStudySession session = studyCapability.getStatus(
                context.conversationId(),
                input.sessionId());
        String lesson = session.currentLesson().isBlank() ? "尚未识别" : session.currentLesson();
        String notes = session.notes().isBlank() ? "尚未记录" : abbreviate(session.notes(), 4_000);
        String pluginInfo = formatPluginStatus(session.pluginStatus());

        byte[] qrImageBytes = null;
        String qrInfo = "";
        if (session.status() == OcsStudySession.Status.WAITING_LOGIN) {
            OcsLoginQrRelay qr = studyCapability.getLoginQrRelay(session.sessionId());
            if (qr != null) {
                qrImageBytes = decodeDataUrl(qr.qrDataUrl());
                qrInfo = "\n登录二维码：已获取，正在发送到手机，请用超星 APP 扫码。";
            } else {
                qrInfo = "\n登录二维码：已过期，请重新发起学习会话。";
            }
        }

        GeneratedMedia media = null;
        if (qrImageBytes != null && qrImageBytes.length > 0) {
            media = new GeneratedMedia(
                    GeneratedMediaKind.IMAGE,
                    qrImageBytes,
                    "login-qr.png",
                    0L,
                    "学习通登录二维码");
        }

        String content = "OCS 学习会话 " + session.sessionId()
                + "\n状态：" + session.status()
                + "\n当前视频播放位置：" + session.progressPercent() + "%"
                + "\n当前章节：" + lesson
                + pluginInfo
                + qrInfo
                + "\n最近事件：" + session.lastEvent()
                + statusGuidance(session.status())
                + "\n笔记：\n" + notes;
        return new ToolExecution(content, media);
    }

    private static String statusGuidance(OcsStudySession.Status status) {
        return switch (status) {
            case CREATED -> "\n下一步：默认浏览器尚未领取任务，请确认浏览器已打开课程页面。";
            case LAUNCHING -> "\n下一步：浏览器伴随脚本已领取命令，但尚未确认课程页面已经打开。";
            case WAITING_LOGIN -> "\n下一步：浏览器需要登录学习通，请查看手机收到的二维码并扫码。";
            case DISCOVERING -> "\n下一步：伴随脚本已连接，正在定位课程页面。";
            case PLUGIN_MISSING -> "\n下一步：浏览器中未检测到 OCS 插件，请确认 Tampermonkey 中已安装 OCS 脚本。";
            default -> "";
        };
    }

    private static String formatPluginStatus(OcsPluginStatus pluginStatus) {
        if (pluginStatus == null) {
            return "\nOCS 插件：尚未检测";
        }
        String detection = pluginStatus.detected()
                ? "已检测到 v" + (pluginStatus.version().isBlank() ? "未知" : pluginStatus.version())
                : "未检测到";
        String config = pluginStatus.configApplied() ? "已生效" : "未生效";
        String missing = pluginStatus.missingKeys().isEmpty()
                ? ""
                : "，缺失：" + String.join(", ", pluginStatus.missingKeys());
        return "\nOCS 插件：" + detection + "，配置" + config + missing;
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        // data:image/png;base64,iVBOR...
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

    private static String abbreviate(String value, int maxCharacters) {
        if (value.length() <= maxCharacters) {
            return value;
        }
        return "…" + value.substring(value.length() - maxCharacters + 1);
    }
}
