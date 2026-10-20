package com.weida.bot.wechatilinkbot.infrastructure.learning;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线任务页面获取器测试。
 */
class OfflineTaskPageFetcherTest {

    @Test
    void fetchTaskListHtmlReturnsConfiguredContent() {
        String html = "<html><body>任务列表</body></html>";
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                Map.of("course1", html),
                null,
                null);

        String result = fetcher.fetchTaskListHtml("course1", "class1", "cpi1", "enc1");
        assertThat(result).isEqualTo(html);
    }

    @Test
    void fetchTaskListHtmlReturnsNullForUnknownCourse() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                Map.of("course1", "<html></html>"),
                null,
                null);

        String result = fetcher.fetchTaskListHtml("unknown", "class1", "cpi1", "enc1");
        assertThat(result).isNull();
    }

    @Test
    void fetchTaskListHtmlReturnsNullForNullCourseId() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                Map.of("course1", "<html></html>"),
                null,
                null);

        String result = fetcher.fetchTaskListHtml(null, "class1", "cpi1", "enc1");
        assertThat(result).isNull();
    }

    @Test
    void fetchCourseListHtmlReturnsConfiguredContent() {
        String html = "<html><body>课程列表</body></html>";
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                null,
                null,
                html);

        String result = fetcher.fetchCourseListHtml();
        assertThat(result).isEqualTo(html);
    }

    @Test
    void fetchCourseListHtmlReturnsNullWhenNotConfigured() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                null,
                null,
                null);

        String result = fetcher.fetchCourseListHtml();
        assertThat(result).isNull();
    }

    @Test
    void fetchCoursePageHtmlReturnsConfiguredContent() {
        String html = "<html><body>课程详情</body></html>";
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                null,
                Map.of("course1:class1", html),
                null);

        String result = fetcher.fetchCoursePageHtml("course1", "class1");
        assertThat(result).isEqualTo(html);
    }

    @Test
    void fetchCoursePageHtmlReturnsNullForUnknownKey() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                null,
                Map.of("course1:class1", "<html></html>"),
                null);

        String result = fetcher.fetchCoursePageHtml("course1", "class2");
        assertThat(result).isNull();
    }

    @Test
    void fetchCoursePageHtmlReturnsNullForNullParams() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(
                null,
                Map.of("course1:class1", "<html></html>"),
                null);

        assertThat(fetcher.fetchCoursePageHtml(null, "class1")).isNull();
        assertThat(fetcher.fetchCoursePageHtml("course1", null)).isNull();
    }

    @Test
    void constructorHandlesNullMapsGracefully() {
        OfflineTaskPageFetcher fetcher = new OfflineTaskPageFetcher(null, null, null);

        assertThat(fetcher.fetchTaskListHtml("any", "any", "any", "any")).isNull();
        assertThat(fetcher.fetchCourseListHtml()).isNull();
        assertThat(fetcher.fetchCoursePageHtml("any", "any")).isNull();
    }
}
