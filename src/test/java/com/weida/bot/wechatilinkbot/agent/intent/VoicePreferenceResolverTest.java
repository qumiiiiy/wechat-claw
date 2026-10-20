package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoicePreferenceResolverTest {

    private final VoicePreferenceResolver resolver = new VoicePreferenceResolver();

    @Test
    void resolvesTheLatestExplicitPreferenceFromHistory() {
        List<ConversationMessage> history = List.of(
                message("user", "请换成男声"),
                message("assistant", "好的"),
                message("user", "以后改用女声"),
                message("assistant", "没问题"));

        VoicePreference preference = resolver.resolve(
                history, "请再用语音说一次");

        assertThat(preference).isEqualTo(VoicePreference.FEMALE);
    }

    @Test
    void currentTurnOverridesHistoryAndSupportsDefaultVoice() {
        List<ConversationMessage> history = List.of(
                message("user", "以后改用女声"));

        assertThat(resolver.resolve(history, "换回默认音色"))
                .isEqualTo(VoicePreference.DEFAULT);
        assertThat(resolver.resolve(history, "换成男声"))
                .isEqualTo(VoicePreference.MALE);
    }

    @Test
    void recognizesChildVoiceAndKeepsItForAPlainRetry() {
        List<ConversationMessage> history = List.of(
                message("user", "用孩子的声音说我是祖国的花朵"),
                message("assistant", "我是祖国的花朵。"));

        assertThat(resolver.hasSwitchIntent("用孩子的声音说一句话")).isTrue();
        assertThat(resolver.hasSwitchIntent("这个声音不够孩子气，再试一次")).isTrue();
        assertThat(resolver.resolve(history, "请再试一次"))
                .isEqualTo(VoicePreference.CHILD);
    }

    @Test
    void doesNotTreatAComparisonQuestionAsAVoiceSwitch() {
        assertThat(resolver.hasSwitchIntent("男声和女声有什么区别？")).isFalse();
        assertThat(resolver.hasSwitchIntent("男声和女声分别有什么用？")).isFalse();
        assertThat(resolver.hasSwitchIntent("男声和女声哪个好听一点？")).isFalse();
        assertThat(resolver.hasSwitchIntent("温柔和活泼有什么区别？")).isFalse();
        assertThat(resolver.hasSwitchIntent("请切换成女声")).isTrue();
    }

    @Test
    void recognizesBroadVoiceProfilesAndSpecificProfilesWin() {
        Map<String, VoicePreference> expectationMap = new LinkedHashMap<>();
        expectationMap.put("请用男童声说一句", VoicePreference.BOY);
        expectationMap.put("换成萝莉音", VoicePreference.GIRL);
        expectationMap.put("改用清爽男声", VoicePreference.YOUNG_MALE);
        expectationMap.put("我要成熟男声", VoicePreference.MATURE_MALE);
        expectationMap.put("请用老奶奶声音读", VoicePreference.ELDERLY_FEMALE);
        expectationMap.put("声音再活泼一点", VoicePreference.ENERGETIC_FEMALE);
        expectationMap.put("换成温柔女声", VoicePreference.GENTLE_FEMALE);
        expectationMap.put("请用女声旁白读", VoicePreference.NARRATOR_FEMALE);
        expectationMap.put("用机器人声音说", VoicePreference.ROBOT);
        expectationMap.put("请用猴哥声音说", VoicePreference.MONKEY_KING);
        expectationMap.put("用粤语说一句", VoicePreference.CANTONESE_MALE);
        expectationMap.put("换成专业干练女声", VoicePreference.PROFESSIONAL_FEMALE);

        expectationMap.forEach((text, expected) -> assertThat(resolver.resolve(List.of(), text))
                .as(text)
                .isEqualTo(expected));
    }

    @Test
    void latestExplicitVoiceProfileWinsWithinTheSameRequest() {
        assertThat(resolver.resolve(
                List.of(), "先用成熟男声，后来改用年轻女声"))
                .isEqualTo(VoicePreference.YOUNG_FEMALE);
    }

    private static ConversationMessage message(String role, String content) {
        Instant now = Instant.now();
        return new ConversationMessage(
                1L, "conversation", role, content, now, now);
    }
}
