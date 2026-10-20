package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("邮件行动分类器")
class EmailActionClassifierTest {

    @Test
    @DisplayName("噪音邮件被过滤")
    void noisePatterns() {
        assertThat(classify("【Newsletter】每周精选")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("unsubscribe from this list")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("验证码：123456")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("系统通知：您的订单已发货")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("noreply@example.com 自动回复")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("限时秒杀特价优惠促销")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("您的信用卡账单已生成")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("微博：有人点赞了你的评论")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("重要安全更新通知")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
    }

    @Test
    @DisplayName("强行动信号识别为需要处理")
    void actionSignals() {
        assertThat(classify("请在本周五前提交报销材料")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
        assertThat(classify("请尽快确认会议安排")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
        assertThat(classify("【急】请回复是否参加")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
        assertThat(classify("交报告 2026年8月7日16:00")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
        assertThat(classify("8月10日前交作业")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
        assertThat(classify("请在8月15日前完成注册")).isEqualTo(EmailActionClassifier.Result.ACTION_REQUIRED);
    }

    @Test
    @DisplayName("普通邮件默认不显示")
    void defaultNotActionable() {
        assertThat(classify("会议纪要：2026-08-05 项目讨论")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("提醒：明天有活动")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("(无主题)")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
        assertThat(classify("周末聚餐照片")).isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
    }

    @Test
    @DisplayName("超过 14 天的邮件过滤")
    void staleFilter() {
        Instant old = Instant.now().minus(15, ChronoUnit.DAYS);
        assertThat(EmailActionClassifier.classify("重要：请处理", "", old))
                .isEqualTo(EmailActionClassifier.Result.NOT_ACTIONABLE);
    }

    private static EmailActionClassifier.Result classify(String text) {
        return EmailActionClassifier.classify(text, "", Instant.now());
    }
}