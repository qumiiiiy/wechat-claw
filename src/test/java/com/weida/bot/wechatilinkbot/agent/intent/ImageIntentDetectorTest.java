package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageIntentDetectorTest {

    private final ImageIntentDetector intentDetector = new ImageIntentDetector();

    @Test
    void recognizesRepeatedImageGenerationRequest() {
        assertThat(intentDetector.hasIntent("再帮我生成一张图片")).isTrue();
        assertThat(intentDetector.hasIntent("请生成一个戴宇航员头盔的猫咪图片")).isTrue();
    }

    @Test
    void recognizesVisualSceneWithoutAnExplicitImageNoun() {
        assertThat(intentDetector.hasIntent("帮我生成一张台风天的西湖")).isTrue();
        assertThat(intentDetector.hasIntent("生成一张课程表")).isFalse();
        assertThat(intentDetector.hasIntent("生成一张本周任务清单")).isFalse();
    }

    @Test
    void doesNotConfuseImageUnderstandingWithGeneration() {
        assertThat(intentDetector.hasIntent("这张图片里有什么？")).isFalse();
        assertThat(intentDetector.hasIntent("我想看看这张图片里有什么")).isFalse();
        assertThat(intentDetector.hasIntent("看看我刚才发的图片")).isFalse();
        assertThat(intentDetector.hasIntent("我想看明天的天气预报")).isFalse();
        assertThat(intentDetector.hasIntent("图片生成技术是什么？")).isFalse();
    }

    @Test
    void recognizesNaturalVisualDesireAsImageGeneration() {
        assertThat(intentDetector.hasIntent("我想看早上天安门升旗的画面")).isTrue();
        assertThat(intentDetector.hasIntent("想看看未来城市的场景")).isTrue();
        assertThat(intentDetector.hasIntent("给我看看海底世界的样子")).isTrue();
        assertThat(intentDetector.hasIntent("展示一下月球基地的视觉效果")).isTrue();
    }

    @Test
    void honorsExplicitNegation() {
        assertThat(intentDetector.hasIntent("不要生成图片，只用文字回答")).isFalse();
    }

    @Test
    void recognizesRetryFromTheImmediatelyPrecedingImageRequest() {
        Instant now = Instant.now();
        List<ConversationMessage> history = List.of(
                new ConversationMessage(
                        1L,
                        "conversation",
                        "user",
                        "请生成一张星空图片",
                        now,
                        now),
                new ConversationMessage(
                        2L,
                        "conversation",
                        "assistant",
                        "已经完成。",
                        now,
                        now));

        assertThat(intentDetector.hasContinuationIntent(
                history, "这个效果不太好，再试一次")).isTrue();
    }
}
