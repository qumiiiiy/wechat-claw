package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailIntentDetectorTest {

    private final EmailIntentDetector detector = new EmailIntentDetector();

    @Test
    void detectsExplicitRequestAndExactConfirmation() {
        assertThat(detector.hasIntent(
                List.of(),
                "帮我把天气发送到邮箱 friend@example.com")).isTrue();
        assertThat(detector.isExactConfirmation("确认发送")).isTrue();
        assertThat(detector.isExactConfirmation("好的，确认发送吧")).isFalse();
        assertThat(detector.hasIntent(
                List.of(),
                "帮我写道歉信，发送给 friend@example.com，雪儿")).isTrue();
        assertThat(detector.hasIntent(
                List.of(),
                "替我写邮件传达给她吧 friend@example.com")).isTrue();
        assertThat(detector.hasIntent(
                List.of(),
                "代我向 friend@example.com，雪儿道声早安")).isTrue();
    }

    @Test
    void keepsNearbyInformationAndRevisionTurnsInTheEmailWorkflow() {
        List<ConversationMessage> history = List.of(
                message("user", "请把今天的天气发送到邮箱 friend@example.com"),
                message("assistant", "请问要查询哪个城市？"));

        assertThat(detector.hasIntent(
                history,
                "就是杭州的天气，对方姓白，希望写得温柔一些")).isTrue();
        assertThat(detector.hasIntent(
                history,
                "你的文采可以再好一点吗")).isTrue();
    }

    @Test
    void keepsDraftRevisionsInWorkflowAfterOriginalRequestLeavesHistory() {
        List<ConversationMessage> history = List.of(
                message("user", "完全无关的第六条消息"),
                message("assistant", "完全无关的回复"));

        assertThat(detector.hasIntent(
                history,
                "行文里面多一些表情和颜文字",
                true)).isTrue();
        assertThat(detector.hasIntent(
                history,
                "行文里面多一些表情和颜文字",
                false)).isFalse();
    }

    @Test
    void recognizesApologyDetailsFromAWechatFramedHistoryTurn() {
        List<ConversationMessage> history = List.of(
                message(
                        "user",
                        wechatPrompt(
                                "帮我写一份道歉信，发送给 friend@example.com，雪儿")),
                message("assistant", "请补充想说明的情况"));

        assertThat(detector.hasIntent(
                history,
                "雪儿是爱称，我最近忙于学校项目上线")).isTrue();
        assertThat(detector.hasIntent(
                history,
                "口吻改成由 bot 替主人道歉，署名保持原来的关系")).isTrue();
    }

    @Test
    void doesNotTreatAnUnrelatedOrCancelledTurnAsEmailAuthorization() {
        List<ConversationMessage> history = List.of(
                message("user", "请发邮件给 friend@example.com"),
                message("assistant", "请补充正文"));

        assertThat(detector.hasIntent(history, "讲个笑话")).isFalse();
        assertThat(detector.hasIntent(history, "再讲个笑话")).isFalse();
        assertThat(detector.hasIntent(history, "取消发送邮件")).isFalse();
    }

    @Test
    void doesNotContinueACompletedEmailWorkflow() {
        List<ConversationMessage> history = List.of(
                message("user", "请把天气发送到邮箱 friend@example.com"),
                message("assistant", "邮件已发送。"));

        assertThat(detector.hasIntent(history, "问一下杭州今天的天气", false)).isFalse();
    }

    @Test
    void doesNotTreatAnEmailRevisionAsAnAttachmentRequest() {
        assertThat(detector.hasAttachmentIntent("把这个邮件的称呼改得亲切一些")).isFalse();
        assertThat(detector.hasAttachmentIntent("请把刚才生成的 PDF 作为附件发送")).isTrue();
        assertThat(detector.hasAttachmentIntent("生成 Excel 后发邮件给我")).isTrue();
        assertThat(detector.hasAttachmentIntent("附件：无")).isFalse();
        assertThat(detector.hasAttachmentIntent("没有附件")).isFalse();
    }

    private static ConversationMessage message(String role, String content) {
        return new ConversationMessage(
                null,
                "conversation-1",
                role,
                content,
                Instant.parse("2026-07-23T05:00:00Z"),
                Instant.parse("2026-07-23T05:00:00Z"));
    }

    private static String wechatPrompt(String text) {
        return "请逐项处理下面这条微信消息，保持原始顺序，不要遗漏重复项；"
                + "视觉附件按消息项出现顺序排列；若信息不足，请明确说明，不要编造。\n"
                + "【消息元数据｜消息项数=1｜发送时间=2026-07-23T06:47:40Z】\n"
                + "【消息项 1/1｜TEXT】\n用户文本：\n"
                + text;
    }
}
