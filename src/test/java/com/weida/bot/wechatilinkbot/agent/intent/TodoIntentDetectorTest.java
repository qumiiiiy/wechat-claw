package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TodoIntentDetectorTest {

    private final TodoIntentDetector detector = new TodoIntentDetector();

    @Test
    void routesSpecificTimeRangesBeforeGenericListPhrases() {
        assertThat(detector.detect("今天有什么任务")).isEqualTo(TodoOperation.TODAY);
        assertThat(detector.detect("明天有什么任务")).isEqualTo(TodoOperation.QUERY);
        assertThat(detector.detect("本周有什么作业")).isEqualTo(TodoOperation.QUERY);
        assertThat(detector.detect("今天要做什么")).isEqualTo(TodoOperation.SUMMARY);
    }

    @Test
    void keepsGenericListAndMutationRoutes() {
        assertThat(detector.detect("我的待办")).isEqualTo(TodoOperation.LIST);
        assertThat(detector.detect("全部")).isEqualTo(TodoOperation.LIST);
        assertThat(detector.detect("待办")).isEqualTo(TodoOperation.LIST);
        assertThat(detector.detect("提醒我明天完成报告")).isEqualTo(TodoOperation.ADD);
        assertThat(detector.detect("完成 TD-123456789abc")).isEqualTo(TodoOperation.COMPLETE);
        assertThat(detector.detect("删除 TD-123456789abc")).isEqualTo(TodoOperation.CANCEL);
    }

    @Test
    void rejectsMultipleTodoCommandsInOneMessageAsAmbiguous() {
        assertThat(detector.detect(
                "提醒我明天18:00提交演示报告\n明天有什么任务\n今日要务"))
                .isEqualTo(TodoOperation.MULTIPLE);
    }

    @Test
    void routesTimeFilteredLearningTaskQueriesToQueryTodos() {
        assertThat(detector.detect("过滤学习通中2026年7月前的未完成任务"))
                .isEqualTo(TodoOperation.QUERY);
        assertThat(detector.detect("只查7月后的作业"))
                .isEqualTo(TodoOperation.QUERY);
        assertThat(detector.detect("7月1日之前的任务"))
                .isEqualTo(TodoOperation.QUERY);
        assertThat(detector.detect("提醒我7月1日前交报告"))
                .isEqualTo(TodoOperation.ADD);
        assertThat(detector.hasTimeFilter("过滤7月前")).isTrue();
        assertThat(detector.hasTimeFilter("查所有未完成任务")).isFalse();
    }
}
