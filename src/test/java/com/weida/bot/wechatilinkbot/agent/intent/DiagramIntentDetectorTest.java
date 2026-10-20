package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramIntentDetectorTest {

    private final DiagramIntentDetector detector = new DiagramIntentDetector();

    @Test
    void recognizesCommonDiagramKeywords() {
        assertThat(detector.hasIntent("画个时序图")).isTrue();
        assertThat(detector.hasIntent("做个流程图")).isTrue();
        assertThat(detector.hasIntent("类图：User -> Account")).isTrue();
        assertThat(detector.hasIntent("来个ER图")).isTrue();
        assertThat(detector.hasIntent("画一个状态机")).isTrue();
        assertThat(detector.hasIntent("画个思维导图")).isTrue();
        assertThat(detector.hasIntent("画个甘特图")).isTrue();
        assertThat(detector.hasIntent("来一个uml图")).isTrue();
    }

    @Test
    void recognizesRawPlantumlSource() {
        assertThat(detector.hasIntent("画这个：@startuml\nAlice -> Bob\n@enduml"))
                .isTrue();
        assertThat(detector.hasIntent("@startuml alice -> bob @enduml"))
                .isTrue();
        assertThat(detector.hasIntent("看看这个 @startmindmap")).isTrue();
    }

    @Test
    void doesNotConfuseGenericDrawWithDiagramIntent() {
        assertThat(detector.hasIntent("画只猫")).isFalse();
        assertThat(detector.hasIntent("画张风景")).isFalse();
        assertThat(detector.hasIntent("画个logo")).isFalse();
        assertThat(detector.hasIntent("生成一张图片")).isFalse();
        assertThat(detector.hasIntent("画张海报")).isFalse();
    }

    @Test
    void honorsExplicitNegation() {
        assertThat(detector.hasIntent("不要时序图")).isFalse();
        assertThat(detector.hasIntent("不用流程图")).isFalse();
        assertThat(detector.hasIntent("别类图")).isFalse();
    }

    @Test
    void isCaseInsensitiveAndIgnoresWhitespace() {
        assertThat(detector.hasIntent("画 个 时 序 图")).isTrue();
        assertThat(detector.hasIntent("画个 时序图")).isTrue();
        assertThat(detector.hasIntent("画个SEQUENCE 图")).isFalse();
    }

    @Test
    void recognizesRetryFromTheImmediatelyPrecedingDiagramRequest() {
        Instant now = Instant.now();
        List<ConversationMessage> history = List.of(
                new ConversationMessage(
                        1L,
                        "conversation",
                        "user",
                        "画个时序图：用户->Bot",
                        now,
                        now),
                new ConversationMessage(
                        2L,
                        "conversation",
                        "assistant",
                        "已经画好了。",
                        now,
                        now));
        assertThat(detector.hasContinuationIntent(history, "再试一次")).isTrue();
        assertThat(detector.hasContinuationIntent(history, "重画一次")).isFalse();
        assertThat(detector.hasContinuationIntent(history, "再试一次"))
                .isEqualTo(true);
    }

    @Test
    void doesNotRetryWhenPreviousTurnWasNotDiagram() {
        Instant now = Instant.now();
        List<ConversationMessage> history = List.of(
                new ConversationMessage(
                        1L,
                        "conversation",
                        "user",
                        "画只猫",
                        now,
                        now),
                new ConversationMessage(
                        2L,
                        "conversation",
                        "assistant",
                        "已经画好。",
                        now,
                        now));
        assertThat(detector.hasContinuationIntent(history, "再试一次")).isFalse();
    }

    @Test
    void handlesNullAndBlankInput() {
        assertThat(detector.hasIntent(null)).isFalse();
        assertThat(detector.hasIntent("")).isFalse();
        assertThat(detector.hasIntent("   ")).isFalse();
    }
}